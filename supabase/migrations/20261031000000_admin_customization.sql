-- Yoru: кастомизация профиля в стиле Discord (баннер, «обо мне», местоимения, цвета темы),
-- загрузка аватаров и баннеров (PNG/JPEG/WebP/GIF/WebM) в Supabase Storage, админ-панель.
--
-- Доступ к админ-панели выдаётся только через базу (SQL Editor):
--   insert into public.admins (user_id, note) select id, 'владелец' from public.profiles where username = 'ваш_ник';
-- Забрать доступ:
--   delete from public.admins where user_id = (select id from public.profiles where username = 'ник');

-- ───────────────────────── Профиль: кастомизация ─────────────────────────
alter table public.profiles add column if not exists bio text not null default '';
alter table public.profiles add column if not exists pronouns text not null default '';
alter table public.profiles add column if not exists banner_url text not null default '';
alter table public.profiles add column if not exists theme_primary text not null default '';
alter table public.profiles add column if not exists theme_accent text not null default '';
alter table public.profiles add column if not exists banned boolean not null default false;
alter table public.profiles add column if not exists ban_reason text not null default '';

do $$ begin alter table public.profiles add constraint profiles_bio_check check (char_length(bio) <= 190); exception when duplicate_object then null; end $$;
do $$ begin alter table public.profiles add constraint profiles_pronouns_check check (char_length(pronouns) <= 20); exception when duplicate_object then null; end $$;
do $$ begin alter table public.profiles add constraint profiles_banner_check check (banner_url = '' or (banner_url ~ '^https://' and char_length(banner_url) <= 500)); exception when duplicate_object then null; end $$;
do $$ begin alter table public.profiles add constraint profiles_theme_check check ((theme_primary = '' or theme_primary ~ '^#[0-9A-Fa-f]{6}$') and (theme_accent = '' or theme_accent ~ '^#[0-9A-Fa-f]{6}$')); exception when duplicate_object then null; end $$;

-- Обновление своего профиля; null — оставить поле как есть.
drop function if exists public.update_profile(text, text, text, jsonb);
create or replace function public.update_profile(p_display_name text default null, p_status text default null,
  p_avatar_url text default null, p_showcase jsonb default null, p_bio text default null, p_pronouns text default null,
  p_banner_url text default null, p_theme_primary text default null, p_theme_accent text default null)
returns public.profiles language plpgsql security definer set search_path = public as $$
declare me uuid := auth.uid(); result public.profiles;
begin
  if me is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  if exists (select 1 from profiles where id = me and banned) then raise exception 'banned' using errcode = '42501'; end if;
  update profiles set
    display_name  = coalesce(btrim(p_display_name), display_name),
    status        = coalesce(btrim(p_status), status),
    avatar_url    = coalesce(p_avatar_url, avatar_url),
    showcase      = coalesce(p_showcase, showcase),
    bio           = coalesce(btrim(p_bio), bio),
    pronouns      = coalesce(btrim(p_pronouns), pronouns),
    banner_url    = coalesce(p_banner_url, banner_url),
    theme_primary = coalesce(p_theme_primary, theme_primary),
    theme_accent  = coalesce(p_theme_accent, theme_accent)
  where id = me returning * into result;
  return result;
end $$;
revoke execute on function public.update_profile(text, text, text, jsonb, text, text, text, text, text) from public, anon;
grant execute on function public.update_profile(text, text, text, jsonb, text, text, text, text, text) to authenticated;

-- Новые поля видны в публичном профиле.
create or replace function public.public_profile(p_username text)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'profile', jsonb_build_object('id', pr.id, 'username', pr.username, 'display_name', pr.display_name, 'status', pr.status,
      'avatar_url', pr.avatar_url, 'showcase', pr.showcase, 'equipped_frame', pr.equipped_frame, 'equipped_badge', pr.equipped_badge,
      'equipped_color', pr.equipped_color, 'total_earned', pr.total_earned, 'episodes_watched', pr.episodes_watched,
      'streak_days', pr.streak_days, 'streak_best', pr.streak_best, 'created_at', pr.created_at,
      'bio', pr.bio, 'pronouns', pr.pronouns, 'banner_url', pr.banner_url, 'theme_primary', pr.theme_primary,
      'theme_accent', pr.theme_accent, 'banned', pr.banned),
    'achievements', coalesce((select jsonb_agg(achievement_id) from user_achievements where user_id = pr.id), '[]'::jsonb),
    'lists', coalesce((select jsonb_object_agg(status, n) from (select status, count(*) n from anime_lists where user_id = pr.id group by status) s), '{}'::jsonb),
    'reviews', (select count(*) from reviews where user_id = pr.id),
    'friendship', coalesce((select case when accepted then 'friend' when requester = auth.uid() then 'outgoing' else 'incoming' end
      from friendships where (requester = auth.uid() and addressee = pr.id) or (requester = pr.id and addressee = auth.uid())), case when pr.id = auth.uid() then 'self' else 'none' end))
  from profiles pr where pr.username = lower(btrim(p_username));
$$;

-- ───────────────────────── Блокировка ─────────────────────────
-- Заблокированный пользователь не получает очки, не покупает, не пишет отзывы и не добавляет друзей.
create or replace function public.block_banned()
returns trigger language plpgsql security definer set search_path = public as $$
declare who uuid := coalesce(to_jsonb(new)->>'user_id', to_jsonb(new)->>'requester')::uuid;
begin
  if exists (select 1 from profiles where id = who and banned) then raise exception 'banned' using errcode = '42501'; end if;
  return new;
end $$;
revoke execute on function public.block_banned() from public, anon, authenticated;
drop trigger if exists block_banned on public.watch_awards;
create trigger block_banned before insert on public.watch_awards for each row execute function public.block_banned();
drop trigger if exists block_banned on public.reviews;
create trigger block_banned before insert or update on public.reviews for each row execute function public.block_banned();
drop trigger if exists block_banned on public.inventory;
create trigger block_banned before insert on public.inventory for each row execute function public.block_banned();
drop trigger if exists block_banned on public.friendships;
create trigger block_banned before insert on public.friendships for each row execute function public.block_banned();

-- Отзывы заблокированных скрыты.
drop policy if exists "reviews are public" on public.reviews;
create policy "reviews are public" on public.reviews for select
  using (not exists (select 1 from public.profiles p where p.id = reviews.user_id and p.banned));

-- ───────────────────────── Хранилище аватаров и баннеров ─────────────────────────
-- Публичный бакет media: файлы лежат в папке пользователя <uid>/…, писать можно только в свою папку.
-- Лимит 8 МБ на файл (клиент дополнительно ограничивает аватар 4 МБ и уменьшает статичные картинки).
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('media', 'media', true, 8388608, array['image/png', 'image/jpeg', 'image/webp', 'image/gif', 'video/webm'])
on conflict (id) do update set public = excluded.public, file_size_limit = excluded.file_size_limit, allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "media own insert" on storage.objects;
create policy "media own insert" on storage.objects for insert to authenticated
  with check (bucket_id = 'media' and (storage.foldername(name))[1] = auth.uid()::text
              and not exists (select 1 from public.profiles where id = auth.uid() and banned));
drop policy if exists "media own update" on storage.objects;
create policy "media own update" on storage.objects for update to authenticated
  using (bucket_id = 'media' and (storage.foldername(name))[1] = auth.uid()::text);
drop policy if exists "media own delete" on storage.objects;
create policy "media own delete" on storage.objects for delete to authenticated
  using (bucket_id = 'media' and (storage.foldername(name))[1] = auth.uid()::text);
drop policy if exists "media own select" on storage.objects;
create policy "media own select" on storage.objects for select to authenticated
  using (bucket_id = 'media' and (storage.foldername(name))[1] = auth.uid()::text);

-- ───────────────────────── Админы ─────────────────────────
create table if not exists public.admins (
  user_id    uuid primary key references public.profiles (id) on delete cascade,
  note       text not null default '',
  granted_at timestamptz not null default now()
);
create table if not exists public.admin_log (
  id         bigint generated always as identity primary key,
  admin_id   uuid references public.profiles (id) on delete set null,
  action     text not null,
  target     uuid,
  details    jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);
alter table public.admins enable row level security;
alter table public.admin_log enable row level security;
revoke all on public.admins, public.admin_log from anon, authenticated;

create or replace function public.is_admin()
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from admins where user_id = auth.uid());
$$;

create or replace function public.require_admin()
returns uuid language plpgsql stable security definer set search_path = public as $$
begin
  if auth.uid() is null or not exists (select 1 from admins where user_id = auth.uid()) then
    raise exception 'admin_only' using errcode = '42501';
  end if;
  return auth.uid();
end $$;

create or replace function public.admin_write_log(p_action text, p_target uuid, p_details jsonb)
returns void language sql security definer set search_path = public as $$
  insert into admin_log (admin_id, action, target, details) values (auth.uid(), p_action, p_target, coalesce(p_details, '{}'::jsonb));
$$;

-- Сводка для главной страницы админки.
create or replace function public.admin_stats()
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  perform require_admin();
  return jsonb_build_object(
    'users', (select count(*) from profiles),
    'new_today', (select count(*) from profiles where created_at > now() - interval '24 hours'),
    'banned', (select count(*) from profiles where banned),
    'episodes_today', (select count(*) from watch_awards where awarded_at > now() - interval '24 hours'),
    'episodes_total', (select count(*) from watch_awards),
    'reviews', (select count(*) from reviews),
    'friendships', (select count(*) from friendships where accepted),
    'points_in_circulation', (select coalesce(sum(points), 0) from profiles),
    'top', coalesce((select jsonb_agg(t) from (select username, display_name, total_earned, episodes_watched from profiles order by total_earned desc limit 5) t), '[]'::jsonb));
end $$;

-- Поиск пользователей (по нику, имени или почте).
create or replace function public.admin_users(p_query text default '', p_limit integer default 50)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  perform require_admin();
  return coalesce((select jsonb_agg(u order by u->>'created_at' desc) from (
    select jsonb_build_object('id', p.id, 'username', p.username, 'display_name', p.display_name, 'email', au.email,
      'points', p.points, 'total_earned', p.total_earned, 'episodes_watched', p.episodes_watched, 'banned', p.banned,
      'ban_reason', p.ban_reason, 'avatar_url', p.avatar_url, 'equipped_frame', p.equipped_frame, 'equipped_color', p.equipped_color,
      'equipped_badge', p.equipped_badge, 'is_admin', exists (select 1 from admins a where a.user_id = p.id), 'created_at', p.created_at) u
    from profiles p left join auth.users au on au.id = p.id
    where coalesce(p_query, '') = '' or p.username ilike '%' || p_query || '%' or p.display_name ilike '%' || p_query || '%' or au.email ilike '%' || p_query || '%'
    order by p.created_at desc limit least(greatest(coalesce(p_limit, 50), 1), 200)) s), '[]'::jsonb);
end $$;

create or replace function public.admin_adjust_points(p_user uuid, p_delta integer, p_reason text default '')
returns public.profiles language plpgsql security definer set search_path = public as $$
declare p public.profiles;
begin
  perform require_admin();
  update profiles set points = greatest(0, points + p_delta), total_earned = greatest(0, total_earned + greatest(p_delta, 0))
  where id = p_user returning * into p;
  if not found then raise exception 'user_not_found' using errcode = 'P0002'; end if;
  perform admin_write_log('adjust_points', p_user, jsonb_build_object('delta', p_delta, 'reason', p_reason));
  return p;
end $$;

create or replace function public.admin_set_banned(p_user uuid, p_banned boolean, p_reason text default '')
returns public.profiles language plpgsql security definer set search_path = public as $$
declare p public.profiles;
begin
  perform require_admin();
  if p_user = auth.uid() then raise exception 'self_action' using errcode = '22023'; end if;
  update profiles set banned = p_banned, ban_reason = case when p_banned then left(coalesce(p_reason, ''), 200) else '' end
  where id = p_user returning * into p;
  if not found then raise exception 'user_not_found' using errcode = 'P0002'; end if;
  perform admin_write_log(case when p_banned then 'ban' else 'unban' end, p_user, jsonb_build_object('reason', p_reason));
  return p;
end $$;

-- Сброс оформления, нарушающего правила: avatar, banner, bio, status, display_name, showcase.
create or replace function public.admin_reset_profile(p_user uuid, p_fields text[])
returns public.profiles language plpgsql security definer set search_path = public as $$
declare p public.profiles;
begin
  perform require_admin();
  update profiles set
    avatar_url   = case when 'avatar' = any(p_fields) then '' else avatar_url end,
    banner_url   = case when 'banner' = any(p_fields) then '' else banner_url end,
    bio          = case when 'bio' = any(p_fields) then '' else bio end,
    status       = case when 'status' = any(p_fields) then '' else status end,
    display_name = case when 'display_name' = any(p_fields) then '' else display_name end,
    showcase     = case when 'showcase' = any(p_fields) then '[]'::jsonb else showcase end
  where id = p_user returning * into p;
  if not found then raise exception 'user_not_found' using errcode = 'P0002'; end if;
  perform admin_write_log('reset_profile', p_user, jsonb_build_object('fields', p_fields));
  return p;
end $$;

create or replace function public.admin_grant_decoration(p_user uuid, p_decoration text)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform require_admin();
  if not exists (select 1 from decorations where id = p_decoration) then raise exception 'unknown_decoration' using errcode = 'P0002'; end if;
  insert into inventory (user_id, decoration_id) values (p_user, p_decoration) on conflict do nothing;
  perform admin_write_log('grant_decoration', p_user, jsonb_build_object('decoration', p_decoration));
end $$;

create or replace function public.admin_delete_review(p_user uuid, p_anime_id bigint)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform require_admin();
  delete from reviews where user_id = p_user and anime_id = p_anime_id;
  perform admin_write_log('delete_review', p_user, jsonb_build_object('anime_id', p_anime_id));
end $$;

-- Создание или изменение украшения (например, сезонного) из админки.
create or replace function public.admin_upsert_decoration(p_decoration jsonb)
returns public.decorations language plpgsql security definer set search_path = public as $$
declare d public.decorations;
begin
  perform require_admin();
  insert into decorations (id, kind, title, description, price, min_level, rarity, style, sort, active, exclusive, available_from, available_until)
  values (p_decoration->>'id', p_decoration->>'kind', p_decoration->>'title', coalesce(p_decoration->>'description', ''),
    coalesce((p_decoration->>'price')::int, 0), coalesce((p_decoration->>'min_level')::int, 1), coalesce(p_decoration->>'rarity', 'common'),
    coalesce(p_decoration->'style', '{}'::jsonb), coalesce((p_decoration->>'sort')::int, 500), coalesce((p_decoration->>'active')::boolean, true),
    coalesce((p_decoration->>'exclusive')::boolean, false), nullif(p_decoration->>'available_from', '')::timestamptz, nullif(p_decoration->>'available_until', '')::timestamptz)
  on conflict (id) do update set kind = excluded.kind, title = excluded.title, description = excluded.description, price = excluded.price,
    min_level = excluded.min_level, rarity = excluded.rarity, style = excluded.style, sort = excluded.sort, active = excluded.active,
    exclusive = excluded.exclusive, available_from = excluded.available_from, available_until = excluded.available_until
  returning * into d;
  perform admin_write_log('upsert_decoration', null, p_decoration);
  return d;
end $$;

create or replace function public.admin_recent_reviews(p_limit integer default 50)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  perform require_admin();
  return coalesce((select jsonb_agg(r) from (
    select r.user_id, p.username, r.anime_id, r.title, r.score, r.body, r.updated_at from reviews r join profiles p on p.id = r.user_id
    order by r.updated_at desc limit least(greatest(coalesce(p_limit, 50), 1), 200)) r), '[]'::jsonb);
end $$;

create or replace function public.admin_log_list(p_limit integer default 100)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  perform require_admin();
  return coalesce((select jsonb_agg(l) from (
    select l.created_at, a.username as admin, l.action, t.username as target, l.details
    from admin_log l left join profiles a on a.id = l.admin_id left join profiles t on t.id = l.target
    order by l.created_at desc limit least(greatest(coalesce(p_limit, 100), 1), 500)) l), '[]'::jsonb);
end $$;

revoke execute on function public.require_admin(), public.admin_write_log(text, uuid, jsonb) from public, anon, authenticated;
revoke execute on function public.is_admin(), public.admin_stats(), public.admin_users(text, integer), public.admin_adjust_points(uuid, integer, text),
  public.admin_set_banned(uuid, boolean, text), public.admin_reset_profile(uuid, text[]), public.admin_grant_decoration(uuid, text),
  public.admin_delete_review(uuid, bigint), public.admin_upsert_decoration(jsonb), public.admin_recent_reviews(integer), public.admin_log_list(integer) from public, anon;
grant execute on function public.is_admin(), public.admin_stats(), public.admin_users(text, integer), public.admin_adjust_points(uuid, integer, text),
  public.admin_set_banned(uuid, boolean, text), public.admin_reset_profile(uuid, text[]), public.admin_grant_decoration(uuid, text),
  public.admin_delete_review(uuid, bigint), public.admin_upsert_decoration(jsonb), public.admin_recent_reviews(integer), public.admin_log_list(integer) to authenticated;

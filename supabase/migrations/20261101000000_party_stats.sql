-- Yoru 0.9: украшения как в Discord (украшение аватара, эффект профиля, рамка профиля),
-- совместный просмотр, метки опенинга/эндинга, комментарии к серии с реакциями, подарки друзьям,
-- недельный рейтинг, «Год в Yoru», статистика профиля, рекомендации от друзей, статус «Отложено»
-- и перенос списков со старого сайта YummyAnime. Применяется после 20261031000000_admin_customization.sql.

-- ───────────────────────── Украшения как в Discord ─────────────────────────
-- avatar_deco    — рисунок поверх и вокруг аватара (ушки, нимб, корона…);
-- profile_effect — анимированный слой поверх карточки профиля (искры, сакура, снег…);
-- profile_frame  — орнаментальная рамка вокруг всей карточки.
-- style.type — процедурный стиль, который рисует клиент; style.image_url — своя картинка (PNG/GIF/WebP)
-- из админки, рисуется вместо процедурного стиля.
alter table public.decorations drop constraint if exists decorations_kind_check;
alter table public.decorations add constraint decorations_kind_check
  check (kind in ('frame', 'badge', 'name_color', 'avatar_deco', 'profile_effect', 'profile_frame'));

alter table public.profiles add column if not exists equipped_deco text not null default '';
alter table public.profiles add column if not exists equipped_effect text not null default '';
alter table public.profiles add column if not exists equipped_pframe text not null default '';

create or replace function public.equip_decoration(p_kind text, p_id text)
returns public.profiles language plpgsql security definer set search_path = public as $$
declare me uuid := auth.uid(); d public.decorations; p public.profiles;
begin
  if me is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  if p_kind not in ('frame', 'badge', 'name_color', 'avatar_deco', 'profile_effect', 'profile_frame') then
    raise exception 'bad_kind' using errcode = '22023';
  end if;
  if coalesce(p_id, '') <> '' then
    select * into d from decorations where id = p_id and kind = p_kind;
    if not found then raise exception 'unknown_decoration' using errcode = 'P0002'; end if;
    if (d.price > 0 or d.exclusive) and not exists (select 1 from inventory where user_id = me and decoration_id = d.id) then
      raise exception 'not_owned' using errcode = '42501';
    end if;
  end if;
  update profiles set
    equipped_frame  = case when p_kind = 'frame' then coalesce(nullif(p_id, ''), 'frame_moon') else equipped_frame end,
    equipped_badge  = case when p_kind = 'badge' then coalesce(p_id, '') else equipped_badge end,
    equipped_color  = case when p_kind = 'name_color' then coalesce(nullif(p_id, ''), 'color_default') else equipped_color end,
    equipped_deco   = case when p_kind = 'avatar_deco' then coalesce(p_id, '') else equipped_deco end,
    equipped_effect = case when p_kind = 'profile_effect' then coalesce(p_id, '') else equipped_effect end,
    equipped_pframe = case when p_kind = 'profile_frame' then coalesce(p_id, '') else equipped_pframe end
  where id = me returning * into p;
  return p;
end $$;

-- ───────────────────────── Общие сведения об аниме ─────────────────────────
-- Название, постер, жанры и число серий, которые клиенты сообщают при открытии аниме.
-- Нужны для статистики по жанрам, рекомендаций и «Года в Yoru».
create table if not exists public.anime_meta (
  anime_id   bigint primary key check (anime_id > 0),
  title      text not null default '' check (char_length(title) <= 200),
  poster     text not null default '' check (poster = '' or (poster ~ '^https://' and char_length(poster) <= 500)),
  genres     text[] not null default '{}' check (cardinality(genres) <= 16),
  episodes   integer not null default 0 check (episodes >= 0),
  updated_at timestamptz not null default now()
);
alter table public.anime_meta enable row level security;
drop policy if exists "anime meta is public" on public.anime_meta;
create policy "anime meta is public" on public.anime_meta for select using (true);

create or replace function public.clean_genres(p_genres text[])
returns text[] language sql immutable set search_path = public as $$
  select coalesce((select array_agg(g) from (
    select distinct left(btrim(x), 40) g from unnest(coalesce(p_genres, '{}')) x where btrim(coalesce(x, '')) <> '' limit 16) s), '{}');
$$;

create or replace function public.clean_poster(p_poster text)
returns text language sql immutable set search_path = public as $$
  select case when coalesce(p_poster, '') ~ '^//' then 'https:' || p_poster
              when coalesce(p_poster, '') ~ '^https://' then p_poster else '' end;
$$;

create or replace function public.note_anime(p_anime_id bigint, p_title text default '', p_poster text default '',
  p_genres text[] default null, p_episodes integer default 0)
returns void language plpgsql security definer set search_path = public as $$
declare poster text := clean_poster(p_poster);
begin
  if auth.uid() is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  if p_anime_id is null or p_anime_id <= 0 then raise exception 'bad_anime' using errcode = '22023'; end if;
  if char_length(poster) > 500 then poster := ''; end if;
  insert into anime_meta (anime_id, title, poster, genres, episodes)
  values (p_anime_id, left(coalesce(p_title, ''), 200), poster, clean_genres(p_genres), greatest(coalesce(p_episodes, 0), 0))
  on conflict (anime_id) do update set
    title = coalesce(nullif(excluded.title, ''), anime_meta.title),
    poster = coalesce(nullif(excluded.poster, ''), anime_meta.poster),
    genres = case when cardinality(excluded.genres) > 0 then excluded.genres else anime_meta.genres end,
    episodes = greatest(anime_meta.episodes, excluded.episodes), updated_at = now();
end $$;

-- ───────────────────────── Статус «Отложено» и избранное ─────────────────────────
alter table public.anime_lists drop constraint if exists anime_lists_status_check;
alter table public.anime_lists add constraint anime_lists_status_check check (status in ('watching', 'planned', 'completed', 'on_hold', 'dropped'));
alter table public.anime_lists add column if not exists favorite boolean not null default false;

create or replace function public.set_list_status(p_anime_id bigint, p_status text, p_title text default '', p_poster text default '', p_total integer default 0)
returns jsonb language plpgsql security definer set search_path = public as $$
declare me uuid := auth.uid(); unlocked jsonb;
begin
  if me is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  if coalesce(p_status, '') = '' then
    delete from anime_lists where user_id = me and anime_id = p_anime_id;
    return jsonb_build_object('status', '', 'achievements', '[]'::jsonb);
  end if;
  if p_status not in ('watching', 'planned', 'completed', 'on_hold', 'dropped') then raise exception 'bad_status' using errcode = '22023'; end if;
  insert into anime_lists (user_id, anime_id, status, title, poster, total)
  values (me, p_anime_id, p_status, left(coalesce(p_title, ''), 200),
          case when char_length(clean_poster(p_poster)) <= 500 then clean_poster(p_poster) else '' end, greatest(coalesce(p_total, 0), 0))
  on conflict (user_id, anime_id) do update set status = excluded.status,
    title = coalesce(nullif(excluded.title, ''), anime_lists.title), poster = coalesce(nullif(excluded.poster, ''), anime_lists.poster),
    total = greatest(anime_lists.total, excluded.total), updated_at = now();
  unlocked := grant_achievements(me);
  return jsonb_build_object('status', p_status, 'achievements', unlocked);
end $$;

create or replace function public.set_favorite(p_anime_id bigint, p_favorite boolean)
returns void language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  update anime_lists set favorite = coalesce(p_favorite, false), updated_at = now() where user_id = auth.uid() and anime_id = p_anime_id;
  if not found then raise exception 'not_in_list' using errcode = 'P0002'; end if;
end $$;

-- ───────────────────────── Перенос со старого сайта YummyAnime ─────────────────────────
-- p_items: [{anime_id, status, title, poster, genres, score, favorite, episodes}], до 3000 записей.
-- Существующие записи Yoru не перезаписываются (кроме отметки «любимое»), очки за перенос не начисляются,
-- оценка переносится как отзыв без текста, если своего отзыва ещё нет.
create or replace function public.import_lists(p_items jsonb)
returns jsonb language plpgsql security definer set search_path = public as $$
declare me uuid := auth.uid(); item jsonb; added integer := 0; skipped integer := 0; rated integer := 0;
  aid bigint; st text; sc integer; poster text; title text; inserted boolean;
begin
  if me is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  if exists (select 1 from profiles where id = me and banned) then raise exception 'banned' using errcode = '42501'; end if;
  if jsonb_typeof(p_items) <> 'array' or jsonb_array_length(p_items) > 3000 then raise exception 'bad_import' using errcode = '22023'; end if;
  for item in select * from jsonb_array_elements(p_items) loop
    aid := nullif(item->>'anime_id', '')::bigint;
    st := item->>'status';
    if aid is null or aid <= 0 or st not in ('watching', 'planned', 'completed', 'on_hold', 'dropped') then skipped := skipped + 1; continue; end if;
    title := left(coalesce(item->>'title', ''), 200);
    poster := clean_poster(item->>'poster'); if char_length(poster) > 500 then poster := ''; end if;
    insert into anime_meta (anime_id, title, poster, genres)
    values (aid, title, poster, clean_genres(array(select jsonb_array_elements_text(coalesce(item->'genres', '[]'::jsonb)))))
    on conflict (anime_id) do update set title = coalesce(nullif(anime_meta.title, ''), excluded.title),
      poster = coalesce(nullif(anime_meta.poster, ''), excluded.poster),
      genres = case when cardinality(anime_meta.genres) = 0 then excluded.genres else anime_meta.genres end;
    insert into anime_lists (user_id, anime_id, status, title, poster, episodes, favorite)
    values (me, aid, st, title, poster, greatest(coalesce((item->>'episodes')::int, 0), 0), coalesce((item->>'favorite')::boolean, false))
    on conflict (user_id, anime_id) do update set favorite = anime_lists.favorite or excluded.favorite
    returning (xmax = 0) into inserted;
    if inserted then added := added + 1; end if;
    sc := coalesce(round((item->>'score')::numeric)::int, 0);
    if sc between 1 and 10 and not exists (select 1 from reviews where user_id = me and anime_id = aid) then
      insert into reviews (user_id, anime_id, score, title, poster) values (me, aid, sc, title, poster);
      rated := rated + 1;
    end if;
  end loop;
  perform grant_achievements(me);
  return jsonb_build_object('imported', added, 'skipped', skipped, 'rated', rated);
end $$;

-- ───────────────────────── Совместный просмотр ─────────────────────────
-- Комната по коду из 6 символов. Любой участник управляет паузой, перемоткой и серией;
-- клиенты опрашивают room_poll раз в 1–2 секунды. Позиция хранится на момент updated_at:
-- текущая = position + (paused ? 0 : server_now − updated_at).
create table if not exists public.watch_rooms (
  code       text primary key check (code ~ '^[A-Z2-9]{6}$'),
  host       uuid not null references public.profiles (id) on delete cascade,
  anime_id   bigint not null check (anime_id > 0),
  title      text not null default '' check (char_length(title) <= 200),
  poster     text not null default '',
  episode    text not null default '1' check (char_length(episode) between 1 and 16),
  video_id   bigint not null default 0,
  position   double precision not null default 0 check (position >= 0),
  paused     boolean not null default true,
  updated_by uuid,
  updated_at timestamptz not null default now(),
  created_at timestamptz not null default now()
);
create table if not exists public.room_members (
  code      text not null references public.watch_rooms (code) on delete cascade,
  user_id   uuid not null references public.profiles (id) on delete cascade,
  joined_at timestamptz not null default now(),
  last_seen timestamptz not null default now(),
  primary key (code, user_id)
);
create table if not exists public.room_messages (
  id         bigserial primary key,
  code       text not null references public.watch_rooms (code) on delete cascade,
  user_id    uuid references public.profiles (id) on delete cascade,
  kind       text not null check (kind in ('chat', 'reaction', 'system')),
  body       text not null check (char_length(body) between 1 and 300),
  created_at timestamptz not null default now()
);
create index if not exists room_messages_code on public.room_messages (code, id);
alter table public.watch_rooms enable row level security;
alter table public.room_members enable row level security;
alter table public.room_messages enable row level security;

create or replace function public.room_state(p_code text)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object('code', r.code, 'host', r.host, 'anime_id', r.anime_id, 'title', r.title, 'poster', r.poster,
    'episode', r.episode, 'video_id', r.video_id, 'position', r.position, 'paused', r.paused,
    'updated_at', r.updated_at, 'updated_by', (select username from profiles where id = r.updated_by),
    'server_now', now(), 'age', extract(epoch from now() - r.updated_at),
    'members', coalesce((select jsonb_agg(jsonb_build_object('id', p.id, 'username', p.username, 'display_name', p.display_name,
        'avatar_url', p.avatar_url, 'equipped_frame', p.equipped_frame, 'equipped_deco', p.equipped_deco,
        'equipped_color', p.equipped_color) order by m.joined_at)
      from room_members m join profiles p on p.id = m.user_id where m.code = r.code and m.last_seen > now() - interval '45 seconds'), '[]'::jsonb))
  from watch_rooms r where r.code = p_code;
$$;
revoke execute on function public.room_state(text) from public, anon, authenticated;

create or replace function public.room_create(p_anime_id bigint, p_title text default '', p_poster text default '',
  p_episode text default '1', p_video_id bigint default 0)
returns jsonb language plpgsql security definer set search_path = public as $$
declare me uuid := auth.uid(); new_code text; alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; i integer;
begin
  if me is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  if exists (select 1 from profiles where id = me and banned) then raise exception 'banned' using errcode = '42501'; end if;
  if p_anime_id is null or p_anime_id <= 0 then raise exception 'bad_anime' using errcode = '22023'; end if;
  -- Старые и пустые комнаты убираем при создании новой.
  delete from watch_rooms where updated_at < now() - interval '12 hours'
    and not exists (select 1 from room_members m where m.code = watch_rooms.code and m.last_seen > now() - interval '10 minutes');
  if (select count(*) from watch_rooms where host = me) >= 3 then
    delete from watch_rooms where code in (select code from watch_rooms where host = me order by updated_at limit 1);
  end if;
  loop
    new_code := '';
    for i in 1..6 loop new_code := new_code || substr(alphabet, 1 + floor(random() * length(alphabet))::int, 1); end loop;
    exit when not exists (select 1 from watch_rooms where code = new_code);
  end loop;
  insert into watch_rooms (code, host, anime_id, title, poster, episode, video_id, updated_by)
  values (new_code, me, p_anime_id, left(coalesce(p_title, ''), 200), left(clean_poster(p_poster), 500),
    left(coalesce(nullif(p_episode, ''), '1'), 16), coalesce(p_video_id, 0), me);
  insert into room_members (code, user_id) values (new_code, me);
  insert into room_messages (code, user_id, kind, body) values (new_code, me, 'system', 'created');
  return room_state(new_code);
end $$;

create or replace function public.room_join(p_code text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare me uuid := auth.uid(); c text := upper(btrim(coalesce(p_code, '')));
begin
  if me is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  if exists (select 1 from profiles where id = me and banned) then raise exception 'banned' using errcode = '42501'; end if;
  if not exists (select 1 from watch_rooms where code = c) then raise exception 'room_not_found' using errcode = 'P0002'; end if;
  if (select count(*) from room_members where code = c and last_seen > now() - interval '45 seconds' and user_id <> me) >= 20 then
    raise exception 'room_full' using errcode = '53400';
  end if;
  insert into room_members (code, user_id) values (c, me) on conflict (code, user_id) do update set last_seen = now();
  insert into room_messages (code, user_id, kind, body) values (c, me, 'system', 'joined');
  return room_state(c);
end $$;

create or replace function public.room_leave(p_code text)
returns void language plpgsql security definer set search_path = public as $$
declare me uuid := auth.uid(); c text := upper(btrim(coalesce(p_code, '')));
begin
  if me is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  delete from room_members where code = c and user_id = me;
  if found then insert into room_messages (code, user_id, kind, body) values (c, me, 'system', 'left'); end if;
end $$;

create or replace function public.room_control(p_code text, p_position double precision, p_paused boolean,
  p_episode text default null, p_video_id bigint default null)
returns jsonb language plpgsql security definer set search_path = public as $$
declare me uuid := auth.uid(); c text := upper(btrim(coalesce(p_code, '')));
begin
  if me is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  if not exists (select 1 from room_members where code = c and user_id = me) then raise exception 'not_in_room' using errcode = '42501'; end if;
  update watch_rooms set position = greatest(coalesce(p_position, 0), 0), paused = coalesce(p_paused, paused),
    episode = coalesce(left(nullif(p_episode, ''), 16), episode), video_id = coalesce(p_video_id, video_id),
    updated_by = me, updated_at = now()
  where code = c;
  update room_members set last_seen = now() where code = c and user_id = me;
  return room_state(c);
end $$;

-- Состояние комнаты и новые сообщения после p_since (id последнего полученного сообщения).
create or replace function public.room_poll(p_code text, p_since bigint default 0)
returns jsonb language plpgsql security definer set search_path = public as $$
declare me uuid := auth.uid(); c text := upper(btrim(coalesce(p_code, ''))); state jsonb;
begin
  if me is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  update room_members set last_seen = now() where code = c and user_id = me;
  if not found then raise exception 'not_in_room' using errcode = '42501'; end if;
  state := room_state(c);
  return state || jsonb_build_object('messages', coalesce((select jsonb_agg(jsonb_build_object('id', m.id, 'kind', m.kind, 'body', m.body,
      'username', p.username, 'display_name', p.display_name, 'equipped_color', p.equipped_color, 'created_at', m.created_at) order by m.id)
    from (select * from room_messages where code = c and id > coalesce(p_since, 0) order by id desc limit 60) m
    left join profiles p on p.id = m.user_id), '[]'::jsonb));
end $$;

create or replace function public.room_say(p_code text, p_kind text, p_body text)
returns void language plpgsql security definer set search_path = public as $$
declare me uuid := auth.uid(); c text := upper(btrim(coalesce(p_code, ''))); body text := btrim(coalesce(p_body, ''));
begin
  if me is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  if exists (select 1 from profiles where id = me and banned) then raise exception 'banned' using errcode = '42501'; end if;
  if not exists (select 1 from room_members where code = c and user_id = me) then raise exception 'not_in_room' using errcode = '42501'; end if;
  if p_kind not in ('chat', 'reaction') or body = '' then raise exception 'bad_message' using errcode = '22023'; end if;
  if p_kind = 'reaction' and body not in ('❤', '😂', '😮', '😭', '🔥', '👏', '💀', '🎃') then raise exception 'bad_reaction' using errcode = '22023'; end if;
  if (select count(*) from room_messages where code = c and user_id = me and created_at > now() - interval '10 seconds') >= 8 then
    raise exception 'too_fast' using errcode = '53400';
  end if;
  insert into room_messages (code, user_id, kind, body) values (c, me, p_kind, left(body, 300));
end $$;

-- ───────────────────────── Метки опенинга и эндинга ─────────────────────────
-- Каждый может отметить начало и конец опенинга/эндинга серии; клиент берёт медиану голосов.
create table if not exists public.intro_marks (
  user_id    uuid not null references public.profiles (id) on delete cascade,
  anime_id   bigint not null check (anime_id > 0),
  episode    text not null check (char_length(episode) between 1 and 16),
  kind       text not null check (kind in ('opening', 'ending')),
  start_s    integer not null check (start_s >= 0),
  end_s      integer not null,
  created_at timestamptz not null default now(),
  primary key (user_id, anime_id, episode, kind),
  check (end_s > start_s and end_s - start_s <= 240)
);
create index if not exists intro_marks_anime on public.intro_marks (anime_id, episode, kind);
alter table public.intro_marks enable row level security;
drop trigger if exists block_banned on public.intro_marks;
create trigger block_banned before insert or update on public.intro_marks for each row execute function public.block_banned();

create or replace function public.mark_intro(p_anime_id bigint, p_episode text, p_kind text, p_start integer, p_end integer)
returns jsonb language plpgsql security definer set search_path = public as $$
declare me uuid := auth.uid();
begin
  if me is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  if p_kind not in ('opening', 'ending') then raise exception 'bad_kind' using errcode = '22023'; end if;
  if p_start is null or p_end is null or p_start < 0 or p_end <= p_start or p_end - p_start > 240 then
    raise exception 'bad_range' using errcode = '22023';
  end if;
  insert into intro_marks (user_id, anime_id, episode, kind, start_s, end_s) values (me, p_anime_id, left(p_episode, 16), p_kind, p_start, p_end)
  on conflict (user_id, anime_id, episode, kind) do update set start_s = excluded.start_s, end_s = excluded.end_s, created_at = now();
  return anime_intro(p_anime_id, p_episode);
end $$;

-- {opening: {start, end, votes, exact}, ending: {...}}; exact = false, если меток этой серии нет
-- и взята медиана по другим сериям (опенинг обычно на одном месте).
create or replace function public.anime_intro(p_anime_id bigint, p_episode text)
returns jsonb language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_object_agg(k.kind, v.mark) filter (where v.mark is not null), '{}'::jsonb)
  from (values ('opening'), ('ending')) k(kind)
  cross join lateral (
    select coalesce(
      (select jsonb_build_object('start', round(percentile_cont(0.5) within group (order by start_s)), 'end', round(percentile_cont(0.5) within group (order by end_s)),
         'votes', count(*), 'exact', true)
       from intro_marks where anime_id = p_anime_id and episode = p_episode and kind = k.kind having count(*) > 0),
      (select jsonb_build_object('start', round(percentile_cont(0.5) within group (order by start_s)), 'end', round(percentile_cont(0.5) within group (order by end_s)),
         'votes', count(*), 'exact', false)
       from intro_marks where anime_id = p_anime_id and kind = k.kind and k.kind = 'opening' having count(*) >= 2)) as mark) v;
$$;

-- ───────────────────────── Комментарии к серии ─────────────────────────
create table if not exists public.episode_comments (
  id         bigserial primary key,
  anime_id   bigint not null check (anime_id > 0),
  episode    text not null check (char_length(episode) between 1 and 16),
  user_id    uuid not null references public.profiles (id) on delete cascade,
  at_second  integer check (at_second is null or at_second >= 0),
  body       text not null check (char_length(body) between 1 and 500),
  spoiler    boolean not null default false,
  created_at timestamptz not null default now()
);
create index if not exists episode_comments_episode on public.episode_comments (anime_id, episode, created_at desc);
create table if not exists public.comment_reactions (
  comment_id bigint not null references public.episode_comments (id) on delete cascade,
  user_id    uuid not null references public.profiles (id) on delete cascade,
  emoji      text not null check (emoji in ('❤', '😂', '😮', '😭', '🔥', '👍')),
  primary key (comment_id, user_id, emoji)
);
alter table public.episode_comments enable row level security;
alter table public.comment_reactions enable row level security;
drop trigger if exists block_banned on public.episode_comments;
create trigger block_banned before insert on public.episode_comments for each row execute function public.block_banned();
drop trigger if exists block_banned on public.comment_reactions;
create trigger block_banned before insert on public.comment_reactions for each row execute function public.block_banned();

create or replace function public.post_comment(p_anime_id bigint, p_episode text, p_body text, p_at_second integer default null, p_spoiler boolean default false)
returns bigint language plpgsql security definer set search_path = public as $$
declare me uuid := auth.uid(); new_id bigint; body text := btrim(coalesce(p_body, ''));
begin
  if me is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  if body = '' then raise exception 'empty_comment' using errcode = '22023'; end if;
  if (select count(*) from episode_comments where user_id = me and created_at > now() - interval '1 minute') >= 5 then
    raise exception 'too_fast' using errcode = '53400';
  end if;
  insert into episode_comments (anime_id, episode, user_id, at_second, body, spoiler)
  values (p_anime_id, left(coalesce(nullif(p_episode, ''), '1'), 16), me, case when p_at_second >= 0 then p_at_second end, left(body, 500), coalesce(p_spoiler, false))
  returning id into new_id;
  return new_id;
end $$;

create or replace function public.delete_comment(p_id bigint)
returns void language plpgsql security definer set search_path = public as $$
declare owner uuid;
begin
  if auth.uid() is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  select user_id into owner from episode_comments where id = p_id;
  if not found then return; end if;
  if owner <> auth.uid() and not is_admin() then raise exception 'not_owner' using errcode = '42501'; end if;
  delete from episode_comments where id = p_id;
  if owner <> auth.uid() then perform admin_write_log('delete_comment', owner, jsonb_build_object('comment', p_id)); end if;
end $$;

-- Ставит или снимает реакцию; возвращает true, если реакция теперь стоит.
create or replace function public.react_comment(p_id bigint, p_emoji text)
returns boolean language plpgsql security definer set search_path = public as $$
declare me uuid := auth.uid();
begin
  if me is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  delete from comment_reactions where comment_id = p_id and user_id = me and emoji = p_emoji;
  if found then return false; end if;
  insert into comment_reactions (comment_id, user_id, emoji) values (p_id, me, p_emoji);
  return true;
end $$;

create or replace function public.episode_comments(p_anime_id bigint, p_episode text, p_limit integer default 100)
returns jsonb language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(c order by (c->>'at_second') is null, (c->>'at_second')::int, c->>'created_at' desc), '[]'::jsonb) from (
    select jsonb_build_object('id', e.id, 'body', e.body, 'at_second', e.at_second, 'spoiler', e.spoiler, 'created_at', e.created_at,
      'username', p.username, 'display_name', p.display_name, 'avatar_url', p.avatar_url, 'equipped_frame', p.equipped_frame,
      'equipped_deco', p.equipped_deco, 'equipped_color', p.equipped_color, 'equipped_badge', p.equipped_badge,
      'mine', e.user_id = auth.uid(),
      'reactions', coalesce((select jsonb_object_agg(emoji, n) from (select emoji, count(*) n from comment_reactions where comment_id = e.id group by emoji) r), '{}'::jsonb),
      'my_reactions', coalesce((select jsonb_agg(emoji) from comment_reactions where comment_id = e.id and user_id = auth.uid()), '[]'::jsonb)) c
    from episode_comments e join profiles p on p.id = e.user_id
    where e.anime_id = p_anime_id and e.episode = p_episode and not p.banned
    order by e.created_at desc limit least(greatest(coalesce(p_limit, 100), 1), 300)) s;
$$;

-- ───────────────────────── Подарки друзьям ─────────────────────────
create table if not exists public.gifts (
  id            bigserial primary key,
  sender        uuid not null references public.profiles (id) on delete cascade,
  recipient     uuid not null references public.profiles (id) on delete cascade,
  kind          text not null check (kind in ('points', 'decoration')),
  amount        integer not null default 0 check (amount >= 0),
  decoration_id text references public.decorations (id),
  message       text not null default '' check (char_length(message) <= 140),
  seen          boolean not null default false,
  created_at    timestamptz not null default now(),
  check (sender <> recipient)
);
create index if not exists gifts_recipient on public.gifts (recipient, created_at desc);
alter table public.gifts enable row level security;

create or replace function public.are_friends(a uuid, b uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from friendships where accepted and ((requester = a and addressee = b) or (requester = b and addressee = a)));
$$;
revoke execute on function public.are_friends(uuid, uuid) from public, anon, authenticated;

-- Очки: от 10 до 500 за раз, не больше 1000 в сутки. Переданные очки не увеличивают уровень получателя.
create or replace function public.gift_points(p_user uuid, p_amount integer, p_message text default '')
returns jsonb language plpgsql security definer set search_path = public as $$
declare me uuid := auth.uid(); p public.profiles; sent_today integer;
begin
  if me is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  if exists (select 1 from profiles where id = me and banned) then raise exception 'banned' using errcode = '42501'; end if;
  if p_user = me or not are_friends(me, p_user) then raise exception 'not_friends' using errcode = '42501'; end if;
  if coalesce(p_amount, 0) not between 10 and 500 then raise exception 'bad_amount' using errcode = '22023'; end if;
  select coalesce(sum(amount), 0) into sent_today from gifts where sender = me and kind = 'points' and created_at > now() - interval '24 hours';
  if sent_today + p_amount > 1000 then raise exception 'gift_limit' using errcode = '53400'; end if;
  select * into p from profiles where id = me for update;
  if p.points < p_amount then raise exception 'not_enough_points' using errcode = '42501'; end if;
  update profiles set points = points - p_amount where id = me returning * into p;
  update profiles set points = points + p_amount where id = p_user and not banned;
  if not found then raise exception 'user_not_found' using errcode = 'P0002'; end if;
  insert into gifts (sender, recipient, kind, amount, message) values (me, p_user, 'points', p_amount, left(btrim(coalesce(p_message, '')), 140));
  return jsonb_build_object('balance', p.points);
end $$;

-- Украшение: покупается за очки дарителя и сразу попадает в инвентарь друга.
create or replace function public.gift_decoration(p_user uuid, p_decoration text, p_message text default '')
returns jsonb language plpgsql security definer set search_path = public as $$
declare me uuid := auth.uid(); d public.decorations; p public.profiles;
begin
  if me is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  if exists (select 1 from profiles where id = me and banned) then raise exception 'banned' using errcode = '42501'; end if;
  if p_user = me or not are_friends(me, p_user) then raise exception 'not_friends' using errcode = '42501'; end if;
  select * into d from decorations where id = p_decoration and active;
  if not found then raise exception 'unknown_decoration' using errcode = 'P0002'; end if;
  if d.exclusive or d.price = 0 then raise exception 'not_giftable' using errcode = '42501'; end if;
  if (d.available_from is not null and now() < d.available_from) or (d.available_until is not null and now() > d.available_until) then
    raise exception 'not_available' using errcode = '42501';
  end if;
  if exists (select 1 from inventory where user_id = p_user and decoration_id = d.id) then raise exception 'already_owned' using errcode = '23505'; end if;
  select * into p from profiles where id = me for update;
  if yoru_level(p.total_earned) < d.min_level then raise exception 'level_too_low' using errcode = '42501'; end if;
  if p.points < d.price then raise exception 'not_enough_points' using errcode = '42501'; end if;
  update profiles set points = points - d.price where id = me returning * into p;
  insert into inventory (user_id, decoration_id) values (p_user, d.id);
  insert into gifts (sender, recipient, kind, decoration_id, message) values (me, p_user, 'decoration', d.id, left(btrim(coalesce(p_message, '')), 140));
  return jsonb_build_object('balance', p.points);
end $$;

-- Входящие подарки (последние 30), непросмотренные первыми.
create or replace function public.my_gifts()
returns jsonb language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(g order by (g->>'seen')::boolean, g->>'created_at' desc), '[]'::jsonb) from (
    select jsonb_build_object('id', g.id, 'kind', g.kind, 'amount', g.amount, 'decoration_id', g.decoration_id,
      'decoration_title', d.title, 'decoration_kind', d.kind, 'message', g.message, 'seen', g.seen, 'created_at', g.created_at,
      'username', p.username, 'display_name', p.display_name, 'avatar_url', p.avatar_url) g
    from gifts g join profiles p on p.id = g.sender left join decorations d on d.id = g.decoration_id
    where g.recipient = auth.uid() order by g.created_at desc limit 30) s;
$$;

create or replace function public.mark_gifts_seen()
returns void language sql security definer set search_path = public as $$
  update gifts set seen = true where recipient = auth.uid() and not seen;
$$;

-- ───────────────────────── Рейтинг за неделю ─────────────────────────
-- p_scope: friends (я и друзья) или global. Очки и серии за последние p_days дней (1–31).
create or replace function public.leaderboard(p_scope text default 'friends', p_days integer default 7)
returns jsonb language sql stable security definer set search_path = public as $$
  with scope as (
    select id from profiles where not banned and (p_scope = 'global' or id = auth.uid()
      or id in (select case when requester = auth.uid() then addressee else requester end from friendships
                where accepted and auth.uid() in (requester, addressee)))),
  totals as (
    select w.user_id, sum(w.points)::int as points, count(*)::int as episodes, sum(w.seconds)::bigint as seconds
    from watch_awards w join scope s on s.id = w.user_id
    where w.awarded_at > now() - make_interval(days => least(greatest(coalesce(p_days, 7), 1), 31))
    group by w.user_id),
  ranked as (
    select t.*, rank() over (order by t.points desc, t.episodes desc) as place from totals t)
  select coalesce(jsonb_agg(jsonb_build_object('place', r.place, 'points', r.points, 'episodes', r.episodes,
      'hours', round(r.seconds / 3600.0, 1), 'me', r.user_id = auth.uid(),
      'username', p.username, 'display_name', p.display_name, 'avatar_url', p.avatar_url, 'equipped_frame', p.equipped_frame,
      'equipped_deco', p.equipped_deco, 'equipped_color', p.equipped_color, 'equipped_badge', p.equipped_badge,
      'level', yoru_level(p.total_earned)) order by r.place, p.username), '[]'::jsonb)
  from ranked r join profiles p on p.id = r.user_id
  where r.place <= 50 or r.user_id = auth.uid();
$$;

-- ───────────────────────── Статистика профиля ─────────────────────────
create or replace function public.watch_stats(p_username text)
returns jsonb language sql stable security definer set search_path = public as $$
  with u as (select id, episodes_watched, streak_best from profiles where username = lower(btrim(p_username)))
  select jsonb_build_object(
    'episodes', (select count(*) from watch_awards w where w.user_id = u.id),
    'hours', round(coalesce((select sum(seconds) from watch_awards w where w.user_id = u.id), 0) / 3600.0, 1),
    'anime', (select count(distinct anime_id) from watch_awards w where w.user_id = u.id),
    'completed', (select count(*) from anime_lists l where l.user_id = u.id and l.status = 'completed'),
    'avg_score', (select round(avg(score)::numeric, 2) from reviews r where r.user_id = u.id),
    'scores', coalesce((select jsonb_object_agg(score, n) from (select score, count(*) n from reviews r where r.user_id = u.id group by score) s), '{}'::jsonb),
    'genres', coalesce((select jsonb_agg(jsonb_build_object('genre', g, 'count', n) order by n desc, g) from (
        select g, count(*) n from anime_lists l join anime_meta m on m.anime_id = l.anime_id, unnest(m.genres) g
        where l.user_id = u.id and l.status in ('watching', 'completed', 'on_hold') group by g order by n desc limit 10) gs), '[]'::jsonb),
    'weekdays', (select jsonb_agg(coalesce(n, 0) order by d) from generate_series(1, 7) d
        left join (select extract(isodow from awarded_at + interval '3 hours')::int dow, count(*) n from watch_awards w where w.user_id = u.id group by 1) x on x.dow = d),
    'streak_best', u.streak_best)
  from u;
$$;

-- ───────────────────────── «Год в Yoru» ─────────────────────────
-- Сводка за год в духе Spotify Wrapped. Часы считаются по засчитанным сериям (время по Минску, UTC+3).
create or replace function public.year_summary(p_year integer default null)
returns jsonb language sql stable security definer set search_path = public as $$
  with me as (select * from profiles where id = auth.uid()),
  yr as (select coalesce(p_year, extract(year from now() + interval '3 hours')::int) as y),
  w as (select w.*, w.awarded_at + interval '3 hours' as local_at from watch_awards w, yr
        where w.user_id = auth.uid() and extract(year from w.awarded_at + interval '3 hours') = yr.y),
  per_anime as (select anime_id, count(*) n, sum(seconds) s, min(local_at) first_at from w group by anime_id),
  all_users as (select user_id, count(*) n from watch_awards, yr where extract(year from awarded_at + interval '3 hours') = yr.y group by user_id)
  select jsonb_build_object(
    'year', (select y from yr),
    'episodes', (select count(*) from w),
    'hours', round(coalesce((select sum(seconds) from w), 0) / 3600.0, 1),
    'anime', (select count(*) from per_anime),
    'points', coalesce((select sum(points) from w), 0),
    'months', (select jsonb_agg(coalesce(n, 0) order by m) from generate_series(1, 12) m
               left join (select extract(month from local_at)::int mm, count(*) n from w group by 1) x on x.mm = m),
    'top_month', (select extract(month from local_at)::int from w group by 1 order by count(*) desc, 1 limit 1),
    'night_episodes', (select count(*) from w where extract(hour from local_at) < 5),
    'top_hour', (select extract(hour from local_at)::int from w group by 1 order by count(*) desc, 1 limit 1),
    'best_day', (select jsonb_build_object('date', local_at::date, 'episodes', count(*)) from w group by local_at::date order by count(*) desc, local_at::date limit 1),
    'top_anime', coalesce((select jsonb_agg(jsonb_build_object('anime_id', a.anime_id, 'episodes', a.n, 'hours', round(a.s / 3600.0, 1),
        'title', coalesce(nullif(l.title, ''), m.title, ''), 'poster', coalesce(nullif(l.poster, ''), m.poster, '')) order by a.n desc, a.s desc)
      from (select * from per_anime order by n desc, s desc limit 5) a
      left join anime_lists l on l.user_id = auth.uid() and l.anime_id = a.anime_id left join anime_meta m on m.anime_id = a.anime_id), '[]'::jsonb),
    'first_anime', (select jsonb_build_object('anime_id', a.anime_id, 'title', coalesce(nullif(l.title, ''), m.title, ''),
        'poster', coalesce(nullif(l.poster, ''), m.poster, ''), 'date', a.first_at::date)
      from (select * from per_anime order by first_at limit 1) a
      left join anime_lists l on l.user_id = auth.uid() and l.anime_id = a.anime_id left join anime_meta m on m.anime_id = a.anime_id),
    'genres', coalesce((select jsonb_agg(jsonb_build_object('genre', g, 'episodes', n) order by n desc, g) from (
        select g, sum(a.n) n from per_anime a join anime_meta m on m.anime_id = a.anime_id, unnest(m.genres) g group by g order by 2 desc limit 5) gs), '[]'::jsonb),
    'completed', (select count(*) from anime_lists, yr where user_id = auth.uid() and status = 'completed' and extract(year from updated_at) = yr.y),
    'reviews', (select count(*) from reviews, yr where user_id = auth.uid() and extract(year from created_at) = yr.y),
    'avg_score', (select round(avg(score)::numeric, 1) from reviews, yr where user_id = auth.uid() and extract(year from created_at) = yr.y),
    'streak_best', (select streak_best from me),
    'friends', (select count(*) from friendships where accepted and auth.uid() in (requester, addressee)),
    'top_percent', (select case when (select count(*) from w) = 0 then null else
        greatest(1, ceil(100.0 * (select count(*) from all_users where n > (select count(*) from w)) / greatest((select count(*) from all_users), 1)))::int end),
    'level', (select yoru_level(total_earned) from me));
$$;

-- ───────────────────────── Рекомендации ─────────────────────────
-- Аниме, которые друзья досмотрели или высоко оценили, а у меня их нет в списках.
-- Клиент дополняет их подбором по любимым жанрам из watch_stats.
create or replace function public.friend_recommendations(p_limit integer default 20)
returns jsonb language sql stable security definer set search_path = public as $$
  with friends as (select case when requester = auth.uid() then addressee else requester end id from friendships
                   where accepted and auth.uid() in (requester, addressee)),
  votes as (
    select l.anime_id, count(distinct l.user_id) fans, avg(r.score) score,
      max(coalesce(nullif(l.title, ''), m.title, '')) title, max(coalesce(nullif(l.poster, ''), m.poster, '')) poster,
      (array_agg(p.username order by r.score desc nulls last))[1] by_user
    from anime_lists l join friends f on f.id = l.user_id join profiles p on p.id = l.user_id
    left join reviews r on r.user_id = l.user_id and r.anime_id = l.anime_id
    left join anime_meta m on m.anime_id = l.anime_id
    where l.status in ('completed', 'watching') and (r.score is null or r.score >= 7)
      and not exists (select 1 from anime_lists mine where mine.user_id = auth.uid() and mine.anime_id = l.anime_id)
      and not exists (select 1 from watch_awards seen where seen.user_id = auth.uid() and seen.anime_id = l.anime_id)
    group by l.anime_id)
  select coalesce(jsonb_agg(jsonb_build_object('anime_id', anime_id, 'title', title, 'poster', poster, 'fans', fans,
      'score', round(score::numeric, 1), 'by', by_user) order by fans desc, score desc nulls last), '[]'::jsonb)
  from (select * from votes order by fans desc, score desc nulls last limit least(greatest(coalesce(p_limit, 20), 1), 50)) v;
$$;

-- ───────────────────────── Публичный профиль, друзья и админка с новыми украшениями ─────────────────────────
create or replace function public.public_profile(p_username text)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'profile', jsonb_build_object('id', pr.id, 'username', pr.username, 'display_name', pr.display_name, 'status', pr.status,
      'avatar_url', pr.avatar_url, 'showcase', pr.showcase, 'equipped_frame', pr.equipped_frame, 'equipped_badge', pr.equipped_badge,
      'equipped_color', pr.equipped_color, 'equipped_deco', pr.equipped_deco, 'equipped_effect', pr.equipped_effect,
      'equipped_pframe', pr.equipped_pframe, 'total_earned', pr.total_earned, 'episodes_watched', pr.episodes_watched,
      'streak_days', pr.streak_days, 'streak_best', pr.streak_best, 'created_at', pr.created_at,
      'bio', pr.bio, 'pronouns', pr.pronouns, 'banner_url', pr.banner_url, 'theme_primary', pr.theme_primary,
      'theme_accent', pr.theme_accent, 'banned', pr.banned),
    'achievements', coalesce((select jsonb_agg(achievement_id) from user_achievements where user_id = pr.id), '[]'::jsonb),
    'lists', coalesce((select jsonb_object_agg(status, n) from (select status, count(*) n from anime_lists where user_id = pr.id group by status) s), '{}'::jsonb),
    'favorites', coalesce((select jsonb_agg(jsonb_build_object('anime_id', anime_id, 'title', title, 'poster', poster) order by updated_at desc)
      from (select * from anime_lists where user_id = pr.id and favorite order by updated_at desc limit 12) f), '[]'::jsonb),
    'reviews', (select count(*) from reviews where user_id = pr.id),
    'friendship', coalesce((select case when accepted then 'friend' when requester = auth.uid() then 'outgoing' else 'incoming' end
      from friendships where (requester = auth.uid() and addressee = pr.id) or (requester = pr.id and addressee = auth.uid())), case when pr.id = auth.uid() then 'self' else 'none' end))
  from profiles pr where pr.username = lower(btrim(p_username));
$$;

create or replace function public.my_friends()
returns jsonb language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object(
    'id', pr.id, 'username', pr.username, 'display_name', pr.display_name, 'avatar_url', pr.avatar_url, 'status', pr.status,
    'equipped_frame', pr.equipped_frame, 'equipped_badge', pr.equipped_badge, 'equipped_color', pr.equipped_color,
    'equipped_deco', pr.equipped_deco, 'level', yoru_level(pr.total_earned), 'streak_days', pr.streak_days,
    'state', case when f.accepted then 'friend' when f.addressee = auth.uid() then 'incoming' else 'outgoing' end
  ) order by f.accepted, pr.username), '[]'::jsonb)
  from friendships f join profiles pr on pr.id = case when f.requester = auth.uid() then f.addressee else f.requester end
  where auth.uid() in (f.requester, f.addressee);
$$;

create or replace function public.admin_users(p_query text default '', p_limit integer default 50)
returns jsonb language plpgsql security definer set search_path = public as $$
begin
  perform require_admin();
  return coalesce((select jsonb_agg(u order by u->>'created_at' desc) from (
    select jsonb_build_object('id', p.id, 'username', p.username, 'display_name', p.display_name, 'email', au.email,
      'points', p.points, 'total_earned', p.total_earned, 'episodes_watched', p.episodes_watched, 'banned', p.banned,
      'ban_reason', p.ban_reason, 'avatar_url', p.avatar_url, 'equipped_frame', p.equipped_frame, 'equipped_color', p.equipped_color,
      'equipped_badge', p.equipped_badge, 'equipped_deco', p.equipped_deco, 'equipped_effect', p.equipped_effect,
      'equipped_pframe', p.equipped_pframe, 'is_admin', exists (select 1 from admins a where a.user_id = p.id), 'created_at', p.created_at) u
    from profiles p left join auth.users au on au.id = p.id
    where coalesce(p_query, '') = '' or p.username ilike '%' || p_query || '%' or p.display_name ilike '%' || p_query || '%' or au.email ilike '%' || p_query || '%'
    order by p.created_at desc limit least(greatest(coalesce(p_limit, 50), 1), 200)) s), '[]'::jsonb);
end $$;

create or replace function public.admin_reset_profile(p_user uuid, p_fields text[])
returns public.profiles language plpgsql security definer set search_path = public as $$
declare p public.profiles;
begin
  perform require_admin();
  update profiles set
    avatar_url      = case when 'avatar' = any(p_fields) then '' else avatar_url end,
    banner_url      = case when 'banner' = any(p_fields) then '' else banner_url end,
    bio             = case when 'bio' = any(p_fields) then '' else bio end,
    status          = case when 'status' = any(p_fields) then '' else status end,
    display_name    = case when 'display_name' = any(p_fields) then '' else display_name end,
    showcase        = case when 'showcase' = any(p_fields) then '[]'::jsonb else showcase end,
    equipped_deco   = case when 'decorations' = any(p_fields) then '' else equipped_deco end,
    equipped_effect = case when 'decorations' = any(p_fields) then '' else equipped_effect end,
    equipped_pframe = case when 'decorations' = any(p_fields) then '' else equipped_pframe end
  where id = p_user returning * into p;
  if not found then raise exception 'user_not_found' using errcode = 'P0002'; end if;
  perform admin_write_log('reset_profile', p_user, jsonb_build_object('fields', p_fields));
  return p;
end $$;

-- ───────────────────────── Права ─────────────────────────
-- Таблицы меняются только через функции; читать напрямую можно только общие сведения об аниме.
revoke all on public.anime_meta, public.watch_rooms, public.room_members, public.room_messages, public.intro_marks,
  public.episode_comments, public.comment_reactions, public.gifts from anon, authenticated;
grant select on public.anime_meta to anon, authenticated;
revoke execute on function public.clean_genres(text[]), public.clean_poster(text) from public, anon, authenticated;
revoke execute on function public.note_anime(bigint, text, text, text[], integer), public.set_favorite(bigint, boolean), public.import_lists(jsonb),
  public.room_create(bigint, text, text, text, bigint), public.room_join(text), public.room_leave(text),
  public.room_control(text, double precision, boolean, text, bigint), public.room_poll(text, bigint), public.room_say(text, text, text),
  public.mark_intro(bigint, text, text, integer, integer), public.post_comment(bigint, text, text, integer, boolean),
  public.delete_comment(bigint), public.react_comment(bigint, text), public.gift_points(uuid, integer, text),
  public.gift_decoration(uuid, text, text), public.my_gifts(), public.mark_gifts_seen(), public.leaderboard(text, integer),
  public.year_summary(integer), public.friend_recommendations(integer) from public, anon;
grant execute on function public.note_anime(bigint, text, text, text[], integer), public.set_favorite(bigint, boolean), public.import_lists(jsonb),
  public.room_create(bigint, text, text, text, bigint), public.room_join(text), public.room_leave(text),
  public.room_control(text, double precision, boolean, text, bigint), public.room_poll(text, bigint), public.room_say(text, text, text),
  public.mark_intro(bigint, text, text, integer, integer), public.post_comment(bigint, text, text, integer, boolean),
  public.delete_comment(bigint), public.react_comment(bigint, text), public.gift_points(uuid, integer, text),
  public.gift_decoration(uuid, text, text), public.my_gifts(), public.mark_gifts_seen(), public.leaderboard(text, integer),
  public.year_summary(integer), public.friend_recommendations(integer) to authenticated;
revoke execute on function public.anime_intro(bigint, text), public.episode_comments(bigint, text, integer), public.watch_stats(text) from public;
grant execute on function public.anime_intro(bigint, text), public.episode_comments(bigint, text, integer), public.watch_stats(text) to anon, authenticated;

-- ───────────────────────── Каталог: новые украшения ─────────────────────────
-- Все рисунки оригинальные и рисуются кодом клиента по style.type.
insert into public.decorations (id, kind, title, description, price, min_level, rarity, style, sort, exclusive, available_from, available_until) values
  -- Украшения аватара.
  ('deco_cat_ears',   'avatar_deco', 'Кошачьи ушки',     'Пушистые ушки над аватаром',          250, 1, 'common',    '{"type":"cat_ears","colors":["#FF8FB8","#FFD6E6"]}', 10, false, null, null),
  ('deco_bunny',      'avatar_deco', 'Зайка',            'Длинные мягкие ушки',                 250, 2, 'common',    '{"type":"bunny","colors":["#FFFFFF","#FFC2D6"]}', 20, false, null, null),
  ('deco_halo',       'avatar_deco', 'Нимб',             'Светящееся кольцо, которое парит',    400, 3, 'rare',      '{"type":"halo","colors":["#FFE08A","#FFF6D0"],"animated":true}', 30, false, null, null),
  ('deco_horns',      'avatar_deco', 'Рожки',            'Маленькие демонические рожки',        400, 3, 'rare',      '{"type":"horns","colors":["#FF4D67","#7A1430"]}', 40, false, null, null),
  ('deco_headphones', 'avatar_deco', 'Наушники',         'Для тех, кто смотрит с саундтреком', 450, 4, 'rare',      '{"type":"headphones","colors":["#2B2B36","#FF8A3D"]}', 50, false, null, null),
  ('deco_crown',      'avatar_deco', 'Корона',           'Для короля марафонов',                900, 8, 'epic',      '{"type":"crown","colors":["#FFC768","#FF8A3D","#7EDCFF"]}', 60, false, null, null),
  ('deco_orbit_hearts','avatar_deco','Орбита сердец',    'Сердечки кружат вокруг аватара',     800, 6, 'epic',      '{"type":"orbit","glyph":"♥","colors":["#FF5C8A","#FFB3C8"],"animated":true}', 70, false, null, null),
  ('deco_orbit_stars','avatar_deco', 'Звёздная орбита',  'Звёзды кружат вокруг аватара',       800, 6, 'epic',      '{"type":"orbit","glyph":"★","colors":["#FFE08A","#9FD8FF"],"animated":true}', 80, false, null, null),
  ('deco_sakura',     'avatar_deco', 'Венок сакуры',     'Цветы сакуры по краю аватара',        1200, 10, 'legendary','{"type":"sakura_wreath","colors":["#FFB7CF","#FF7FA8","#FFFFFF"],"animated":true}', 90, false, null, null),
  ('deco_pumpkin',    'avatar_deco', 'Тыквенная шапка',  'Хэллоуин · только до 3 ноября',       350, 1, 'rare',      '{"type":"pumpkin","colors":["#FF8A1F","#3A7D2C","#2B1A3A"]}', 200, false, '2026-10-24', '2026-11-04'),
  -- Эффекты профиля.
  ('fx_sparkles',     'profile_effect', 'Искры',         'Мерцающие искорки поверх профиля',    500, 2, 'rare',      '{"type":"sparkles","colors":["#FFFFFF","#FFE08A","#FF8FB8"]}', 10, false, null, null),
  ('fx_bubbles',      'profile_effect', 'Пузыри',        'Пузырьки всплывают снизу',            500, 2, 'rare',      '{"type":"bubbles","colors":["#9FD8FF","#FFFFFF"]}', 20, false, null, null),
  ('fx_sakura',       'profile_effect', 'Лепестки',      'Медленно падают лепестки сакуры',     900, 5, 'epic',      '{"type":"petals","colors":["#FFB7CF","#FF7FA8"]}', 30, false, null, null),
  ('fx_fireflies',    'profile_effect', 'Светлячки',     'Тёплые огоньки в темноте',            900, 5, 'epic',      '{"type":"fireflies","colors":["#FFE47A","#B8FF8A"]}', 40, false, null, null),
  ('fx_hearts',       'profile_effect', 'Сердечки',      'Сердечки поднимаются вверх',          700, 4, 'rare',      '{"type":"hearts","colors":["#FF5C8A","#FF9EBB"]}', 50, false, null, null),
  ('fx_starfall',     'profile_effect', 'Звездопад',     'Падающие звёзды с хвостами',          1500, 12, 'legendary','{"type":"starfall","colors":["#FFFFFF","#9FD8FF","#FFE08A"]}', 60, false, null, null),
  ('fx_bats',         'profile_effect', 'Летучие мыши',  'Хэллоуин · только до 3 ноября',       450, 1, 'rare',      '{"type":"bats","colors":["#1A1024","#FF8A1F"]}', 200, false, '2026-10-24', '2026-11-04'),
  ('fx_snow',         'profile_effect', 'Снегопад',      'Новый год · 20 декабря — 14 января',  500, 1, 'epic',      '{"type":"snow","colors":["#FFFFFF","#CFEBFF"]}', 210, false, '2026-12-20', '2027-01-15'),
  -- Рамки профиля.
  ('pf_vines',        'profile_frame', 'Лоза',           'Зелёные побеги по краям карточки',    600, 3, 'rare',      '{"type":"vines","colors":["#5FBF77","#B6F0A8"]}', 10, false, null, null),
  ('pf_neon',         'profile_frame', 'Неон',           'Пульсирующая неоновая трубка',        800, 5, 'epic',      '{"type":"neon","colors":["#FF4D67","#FF8A3D","#B388FF"],"animated":true}', 20, false, null, null),
  ('pf_stars',        'profile_frame', 'Созвездие',      'Звёзды и линии созвездий по краю',    800, 5, 'epic',      '{"type":"constellation","colors":["#9FD8FF","#FFFFFF"]}', 30, false, null, null),
  ('pf_flames',       'profile_frame', 'Пламя',          'Языки огня по нижнему краю',          1200, 9, 'legendary','{"type":"flames","colors":["#FFE08A","#FF8A3D","#FF4D67"],"animated":true}', 40, false, null, null),
  ('pf_crystal',      'profile_frame', 'Кристалл',       'Гранёные кристаллы в углах',          1000, 7, 'epic',      '{"type":"crystal","colors":["#B388FF","#7EDCFF","#FFFFFF"]}', 50, false, null, null),
  ('pf_moon',         'profile_frame', 'Лунная ночь',    'Полумесяц и звёзды в уголке',         600, 3, 'rare',      '{"type":"moon","colors":["#FFE9A8","#6F7BFF"]}', 60, false, null, null),
  ('pf_cobweb',       'profile_frame', 'Паутина',        'Хэллоуин · только до 3 ноября',       400, 1, 'rare',      '{"type":"cobweb","colors":["#E6E6F0","#FF8A1F"]}', 200, false, '2026-10-24', '2026-11-04')
on conflict (id) do update set
  kind = excluded.kind, title = excluded.title, description = excluded.description, price = excluded.price, min_level = excluded.min_level,
  rarity = excluded.rarity, style = excluded.style, sort = excluded.sort, exclusive = excluded.exclusive,
  available_from = excluded.available_from, available_until = excluded.available_until;

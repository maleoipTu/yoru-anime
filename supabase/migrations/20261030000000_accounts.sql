-- Yoru: аккаунты, профиль, очки за просмотр и украшения профиля.
-- Применение: Supabase Dashboard → SQL Editor → вставить файл целиком → Run
-- (или `supabase db push`, если используется Supabase CLI).
--
-- Принципы:
--  * клиент может напрямую менять только «безопасные» поля профиля (имя, статус, витрину, аватар);
--  * очки, уровень, покупки и надетые украшения меняются только через функции ниже (security definer);
--  * очки за серию начисляются один раз на пару «аниме + номер серии», с паузой между начислениями и дневным лимитом.

create extension if not exists citext;

-- ───────────────────────── Профиль ─────────────────────────
create table if not exists public.profiles (
  id               uuid primary key references auth.users (id) on delete cascade,
  username         citext not null unique
                   check (username ~ '^[a-z0-9_]{3,20}$'),
  display_name     text not null default ''
                   check (char_length(display_name) <= 32),
  status           text not null default ''
                   check (char_length(status) <= 80),
  avatar_url       text not null default ''
                   check (avatar_url = '' or (avatar_url ~ '^https://' and char_length(avatar_url) <= 500)),
  -- Витрина «любимое аниме»: до 6 элементов {id, title, poster}.
  showcase         jsonb not null default '[]'::jsonb
                   check (jsonb_typeof(showcase) = 'array' and jsonb_array_length(showcase) <= 6 and octet_length(showcase::text) <= 6000),
  points           integer not null default 0 check (points >= 0),
  total_earned     integer not null default 0 check (total_earned >= 0),
  episodes_watched integer not null default 0 check (episodes_watched >= 0),
  equipped_frame   text not null default 'frame_moon',
  equipped_badge   text not null default '',
  equipped_color   text not null default 'color_default',
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

-- ───────────────────────── Украшения ─────────────────────────
-- kind: frame — рамка аватара, badge — значок у ника, name_color — цвет ника.
-- style (рисуется клиентом, новые украшения можно добавлять без обновления приложения):
--   frame:      {"colors": ["#RRGGBB", ...], "animated": bool, "glow": bool}
--   badge:      {"glyph": "★", "color": "#RRGGBB"}
--   name_color: {"colors": ["#RRGGBB", ...]}   (два и более цвета — градиент)
create table if not exists public.decorations (
  id          text primary key check (id ~ '^[a-z0-9_]{2,40}$'),
  kind        text not null check (kind in ('frame', 'badge', 'name_color')),
  title       text not null,
  description text not null default '',
  price       integer not null default 0 check (price >= 0),
  min_level   integer not null default 1 check (min_level >= 1),
  rarity      text not null default 'common' check (rarity in ('common', 'rare', 'epic', 'legendary')),
  style       jsonb not null default '{}'::jsonb,
  sort        integer not null default 0,
  active      boolean not null default true
);

create table if not exists public.inventory (
  user_id       uuid not null references public.profiles (id) on delete cascade,
  decoration_id text not null references public.decorations (id) on delete cascade,
  acquired_at   timestamptz not null default now(),
  primary key (user_id, decoration_id)
);

-- Журнал начислений: одна запись на серию (аниме + номер), повторно очки не даются.
create table if not exists public.watch_awards (
  user_id    uuid not null references public.profiles (id) on delete cascade,
  anime_id   bigint not null check (anime_id > 0),
  episode    text not null check (char_length(episode) between 1 and 16),
  video_id   bigint not null,
  seconds    integer not null,
  points     integer not null,
  awarded_at timestamptz not null default now(),
  primary key (user_id, anime_id, episode)
);
create index if not exists watch_awards_recent on public.watch_awards (user_id, awarded_at desc);

-- ───────────────────────── Правила начисления ─────────────────────────
create or replace function public.yoru_level(total integer)
returns integer language sql immutable as $$
  -- Уровень N требует 50·(N−1)² очков: 1 → 0, 2 → 50, 3 → 200, 4 → 450, 5 → 800 …
  select greatest(1, floor(sqrt(greatest(total, 0) / 50.0))::integer + 1);
$$;

-- ───────────────────────── Права ─────────────────────────
alter table public.profiles     enable row level security;
alter table public.decorations  enable row level security;
alter table public.inventory    enable row level security;
alter table public.watch_awards enable row level security;

drop policy if exists "profiles are public" on public.profiles;
create policy "profiles are public" on public.profiles for select using (true);
drop policy if exists "own profile update" on public.profiles;
create policy "own profile update" on public.profiles for update
  using (auth.uid() = id) with check (auth.uid() = id);

drop policy if exists "decorations are public" on public.decorations;
create policy "decorations are public" on public.decorations for select using (active);

drop policy if exists "inventory is public" on public.inventory;
create policy "inventory is public" on public.inventory for select using (true);

drop policy if exists "own awards" on public.watch_awards;
create policy "own awards" on public.watch_awards for select using (auth.uid() = user_id);

-- Прямые изменения запрещены всем, кроме безопасных столбцов своего профиля.
revoke all on public.profiles, public.decorations, public.inventory, public.watch_awards from anon, authenticated;
grant select on public.profiles, public.decorations, public.inventory to anon, authenticated;
grant select on public.watch_awards to authenticated;
grant update (display_name, status, avatar_url, showcase) on public.profiles to authenticated;

create or replace function public.touch_profile()
returns trigger language plpgsql as $$
begin new.updated_at := now(); return new; end $$;
drop trigger if exists profiles_touch on public.profiles;
create trigger profiles_touch before update on public.profiles
  for each row execute function public.touch_profile();

-- ───────────────────────── Создание профиля при регистрации ─────────────────────────
-- Клиент передаёт ник в options.data.username при signUp. Если ник занят или некорректен,
-- выдаётся запасной user_xxxxxxxx — его можно поменять функцией change_username.
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  wanted text := lower(coalesce(new.raw_user_meta_data ->> 'username', ''));
  fallback text := 'user_' || substr(replace(new.id::text, '-', ''), 1, 8);
begin
  if wanted !~ '^[a-z0-9_]{3,20}$' or exists (select 1 from public.profiles where username = wanted) then
    wanted := fallback;
  end if;
  insert into public.profiles (id, username, display_name)
  values (new.id, wanted, left(coalesce(new.raw_user_meta_data ->> 'display_name', ''), 32));
  return new;
end $$;
drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();

-- ───────────────────────── RPC ─────────────────────────
create or replace function public.username_available(p_username text)
returns boolean language sql stable security definer set search_path = public as $$
  select lower(p_username) ~ '^[a-z0-9_]{3,20}$'
     and not exists (select 1 from public.profiles where username = lower(p_username));
$$;

create or replace function public.change_username(p_username text)
returns public.profiles language plpgsql security definer set search_path = public as $$
declare me uuid := auth.uid(); result public.profiles;
begin
  if me is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  if lower(p_username) !~ '^[a-z0-9_]{3,20}$' then raise exception 'bad_username' using errcode = '22023'; end if;
  if exists (select 1 from profiles where username = lower(p_username) and id <> me) then
    raise exception 'username_taken' using errcode = '23505';
  end if;
  update profiles set username = lower(p_username) where id = me returning * into result;
  return result;
end $$;

-- Обновление «безопасных» полей своего профиля. null — оставить поле как есть.
create or replace function public.update_profile(p_display_name text default null, p_status text default null,
  p_avatar_url text default null, p_showcase jsonb default null)
returns public.profiles language plpgsql security invoker set search_path = public as $$
declare me uuid := auth.uid(); result public.profiles;
begin
  if me is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  update profiles set
    display_name = coalesce(btrim(p_display_name), display_name),
    status       = coalesce(btrim(p_status), status),
    avatar_url   = coalesce(p_avatar_url, avatar_url),
    showcase     = coalesce(p_showcase, showcase)
  where id = me returning * into result;
  return result;
end $$;

-- Начисление очков за серию. Клиент вызывает после того, как серия реально проигрывалась
-- не меньше 5 минут (или почти целиком, если она короче).
-- Ответ: {"awarded": bool, "reason": text, "points": int, "balance": int, "total": int, "level": int}
create or replace function public.award_watch(p_anime_id bigint, p_video_id bigint, p_episode text, p_seconds integer)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  me uuid := auth.uid();
  base_points constant integer := 10;
  new_anime_bonus constant integer := 5;
  daily_limit constant integer := 40;   -- серий в сутки
  cooldown constant interval := interval '4 minutes';
  gained integer;
  last_award timestamptz;
  today_count integer;
  p public.profiles;
begin
  if me is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  if p_anime_id is null or p_anime_id <= 0 or coalesce(p_episode, '') = '' or char_length(p_episode) > 16 then
    raise exception 'bad_episode' using errcode = '22023';
  end if;
  select * into p from profiles where id = me for update;
  if not found then raise exception 'no_profile' using errcode = 'P0002'; end if;

  if coalesce(p_seconds, 0) < 300 then
    return jsonb_build_object('awarded', false, 'reason', 'too_short', 'points', 0, 'balance', p.points, 'total', p.total_earned, 'level', yoru_level(p.total_earned));
  end if;
  if exists (select 1 from watch_awards where user_id = me and anime_id = p_anime_id and episode = p_episode) then
    return jsonb_build_object('awarded', false, 'reason', 'already', 'points', 0, 'balance', p.points, 'total', p.total_earned, 'level', yoru_level(p.total_earned));
  end if;
  select max(awarded_at) into last_award from watch_awards where user_id = me;
  if last_award is not null and now() - last_award < cooldown then
    return jsonb_build_object('awarded', false, 'reason', 'cooldown', 'points', 0, 'balance', p.points, 'total', p.total_earned, 'level', yoru_level(p.total_earned));
  end if;
  select count(*) into today_count from watch_awards where user_id = me and awarded_at > now() - interval '24 hours';
  if today_count >= daily_limit then
    return jsonb_build_object('awarded', false, 'reason', 'daily_limit', 'points', 0, 'balance', p.points, 'total', p.total_earned, 'level', yoru_level(p.total_earned));
  end if;

  gained := base_points;
  if not exists (select 1 from watch_awards where user_id = me and anime_id = p_anime_id) then
    gained := gained + new_anime_bonus;
  end if;
  insert into watch_awards (user_id, anime_id, episode, video_id, seconds, points)
  values (me, p_anime_id, p_episode, coalesce(p_video_id, 0), least(p_seconds, 86400), gained);
  update profiles set points = points + gained, total_earned = total_earned + gained, episodes_watched = episodes_watched + 1
  where id = me returning * into p;
  return jsonb_build_object('awarded', true, 'reason', 'ok', 'points', gained, 'balance', p.points, 'total', p.total_earned, 'level', yoru_level(p.total_earned));
end $$;

create or replace function public.buy_decoration(p_id text)
returns public.profiles language plpgsql security definer set search_path = public as $$
declare me uuid := auth.uid(); d public.decorations; p public.profiles;
begin
  if me is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  select * into d from decorations where id = p_id and active;
  if not found then raise exception 'unknown_decoration' using errcode = 'P0002'; end if;
  select * into p from profiles where id = me for update;
  if exists (select 1 from inventory where user_id = me and decoration_id = d.id) or d.price = 0 then
    raise exception 'already_owned' using errcode = '23505';
  end if;
  if yoru_level(p.total_earned) < d.min_level then raise exception 'level_too_low' using errcode = '42501'; end if;
  if p.points < d.price then raise exception 'not_enough_points' using errcode = '42501'; end if;
  insert into inventory (user_id, decoration_id) values (me, d.id);
  update profiles set points = points - d.price where id = me returning * into p;
  return p;
end $$;

-- p_id = '' снимает украшение (для рамки и цвета возвращается вариант по умолчанию).
create or replace function public.equip_decoration(p_kind text, p_id text)
returns public.profiles language plpgsql security definer set search_path = public as $$
declare me uuid := auth.uid(); d public.decorations; p public.profiles;
begin
  if me is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  if p_kind not in ('frame', 'badge', 'name_color') then raise exception 'bad_kind' using errcode = '22023'; end if;
  if coalesce(p_id, '') <> '' then
    select * into d from decorations where id = p_id and kind = p_kind;
    if not found then raise exception 'unknown_decoration' using errcode = 'P0002'; end if;
    if d.price > 0 and not exists (select 1 from inventory where user_id = me and decoration_id = d.id) then
      raise exception 'not_owned' using errcode = '42501';
    end if;
  end if;
  update profiles set
    equipped_frame = case when p_kind = 'frame' then coalesce(nullif(p_id, ''), 'frame_moon') else equipped_frame end,
    equipped_badge = case when p_kind = 'badge' then coalesce(p_id, '') else equipped_badge end,
    equipped_color = case when p_kind = 'name_color' then coalesce(nullif(p_id, ''), 'color_default') else equipped_color end
  where id = me returning * into p;
  return p;
end $$;

revoke execute on function public.award_watch(bigint, bigint, text, integer), public.buy_decoration(text),
  public.equip_decoration(text, text), public.change_username(text), public.update_profile(text, text, text, jsonb) from public, anon;
grant execute on function public.award_watch(bigint, bigint, text, integer), public.buy_decoration(text),
  public.equip_decoration(text, text), public.change_username(text), public.update_profile(text, text, text, jsonb) to authenticated;
grant execute on function public.username_available(text), public.yoru_level(integer) to anon, authenticated;
revoke execute on function public.handle_new_user() from public, anon, authenticated;

-- ───────────────────────── Каталог украшений ─────────────────────────
insert into public.decorations (id, kind, title, description, price, min_level, rarity, style, sort) values
  ('frame_moon',     'frame', 'Лунный свет',      'Стандартная рамка Yoru',                 0,    1, 'common',    '{"colors":["#BFAEFF"]}', 0),
  ('frame_sakura',   'frame', 'Сакура',           'Нежный розовый переход',                 150,  1, 'common',    '{"colors":["#FFB7D5","#FF8FB8","#FFB7D5"]}', 10),
  ('frame_ocean',    'frame', 'Глубина',          'Бирюза и синева океана',                 300,  2, 'rare',      '{"colors":["#6FE3E1","#3E7BFA","#6FE3E1"]}', 20),
  ('frame_ember',    'frame', 'Пламя',            'Тлеющие угли',                           300,  2, 'rare',      '{"colors":["#FFC768","#FF6A3D","#FFC768"],"glow":true}', 30),
  ('frame_aurora',   'frame', 'Северное сияние',  'Переливается по кругу',                  800,  3, 'epic',      '{"colors":["#7EDCA9","#6FC3FF","#BFAEFF","#7EDCA9"],"animated":true,"glow":true}', 40),
  ('frame_gold',     'frame', 'Золотой век',      'Для тех, кто видел всё',                 1500, 5, 'legendary', '{"colors":["#FFF1B8","#FFC768","#B8862B","#FFC768","#FFF1B8"],"glow":true}', 50),
  ('frame_eclipse',  'frame', 'Затмение',         'Чёрное солнце с золотой короной',        3000, 8, 'legendary', '{"colors":["#1C1530","#FFC768","#1C1530","#BFAEFF","#1C1530"],"animated":true,"glow":true}', 60),

  ('badge_moon',     'badge', 'Полуночник',       'Смотрит, когда все спят',                0,    1, 'common',    '{"glyph":"☾","color":"#BFAEFF"}', 0),
  ('badge_star',     'badge', 'Звезда',           'Первая награда',                         100,  1, 'common',    '{"glyph":"★","color":"#FFC768"}', 10),
  ('badge_sakura',   'badge', 'Сакура',           'Весеннее настроение',                    200,  1, 'common',    '{"glyph":"❀","color":"#FFB7D5"}', 20),
  ('badge_spark',    'badge', 'Искра',            'Яркий зритель',                          250,  2, 'rare',      '{"glyph":"✦","color":"#6FC3FF"}', 30),
  ('badge_blade',    'badge', 'Самурай',          'Путь меча и сезонов',                    400,  3, 'rare',      '{"glyph":"⚔","color":"#E4E6F0"}', 40),
  ('badge_balance',  'badge', 'Баланс',           'Сёнэн и сёдзё в гармонии',               600,  3, 'epic',      '{"glyph":"☯","color":"#F4F2FA"}', 50),
  ('badge_crown',    'badge', 'Король сезона',    'Видел все онгоинги',                     1200, 5, 'legendary', '{"glyph":"♛","color":"#FFC768"}', 60),

  ('color_default',  'name_color', 'Обычный',     'Классический белый',                     0,    1, 'common',    '{"colors":["#F4F2FA"]}', 0),
  ('color_lavender', 'name_color', 'Лаванда',     'Цвет луны Yoru',                         80,   1, 'common',    '{"colors":["#BFAEFF"]}', 10),
  ('color_mint',     'name_color', 'Мята',        'Свежий зелёный',                         80,   1, 'common',    '{"colors":["#7EDCA9"]}', 20),
  ('color_sunset',   'name_color', 'Закат',       'Градиент вечернего неба',                250,  2, 'rare',      '{"colors":["#FF9A8B","#FFC768"]}', 30),
  ('color_sakura',   'name_color', 'Сакура',      'Розовый в лавандовый',                   250,  2, 'rare',      '{"colors":["#FFB7D5","#BFAEFF"]}', 40),
  ('color_aurora',   'name_color', 'Сияние',      'Три цвета северного неба',               700,  3, 'epic',      '{"colors":["#7EDCA9","#6FC3FF","#BFAEFF"]}', 50),
  ('color_gold',     'name_color', 'Золото',      'Для легенд',                             1000, 4, 'legendary', '{"colors":["#FFF1B8","#FFC768","#FFB648"]}', 60)
on conflict (id) do update set
  kind = excluded.kind, title = excluded.title, description = excluded.description, price = excluded.price,
  min_level = excluded.min_level, rarity = excluded.rarity, style = excluded.style, sort = excluded.sort;

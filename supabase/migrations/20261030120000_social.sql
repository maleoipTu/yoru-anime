-- Yoru: списки аниме, достижения, ежедневная серия (streak), сезонные и эксклюзивные украшения,
-- друзья и публичные профили, оценки и отзывы. Применяется после 20261030000000_accounts.sql.

-- ───────────────────────── Профиль: streak ─────────────────────────
alter table public.profiles add column if not exists streak_days integer not null default 0;
alter table public.profiles add column if not exists streak_best integer not null default 0;
alter table public.profiles add column if not exists last_watch_date date;

-- ───────────────────────── Украшения: сезонные и эксклюзивные ─────────────────────────
-- exclusive — не продаётся, выдаётся только за достижение.
-- available_from / available_until — окно продажи сезонного украшения (купленное остаётся навсегда).
alter table public.decorations add column if not exists exclusive boolean not null default false;
alter table public.decorations add column if not exists available_from timestamptz;
alter table public.decorations add column if not exists available_until timestamptz;

-- ───────────────────────── Списки ─────────────────────────
create table if not exists public.anime_lists (
  user_id    uuid not null references public.profiles (id) on delete cascade,
  anime_id   bigint not null check (anime_id > 0),
  status     text not null check (status in ('watching', 'planned', 'completed', 'dropped')),
  title      text not null default '' check (char_length(title) <= 200),
  poster     text not null default '' check (poster = '' or (poster ~ '^https://' and char_length(poster) <= 500)),
  episodes   integer not null default 0 check (episodes >= 0),   -- засчитанных серий
  total      integer not null default 0 check (total >= 0),      -- серий в аниме, если известно
  updated_at timestamptz not null default now(),
  primary key (user_id, anime_id)
);
create index if not exists anime_lists_recent on public.anime_lists (user_id, updated_at desc);

-- ───────────────────────── Достижения ─────────────────────────
create table if not exists public.achievements (
  id          text primary key check (id ~ '^[a-z0-9_]{2,40}$'),
  title       text not null,
  description text not null default '',
  glyph       text not null default '★',
  points      integer not null default 0 check (points >= 0),
  reward      text references public.decorations (id),   -- эксклюзивное украшение за достижение
  sort        integer not null default 0
);
create table if not exists public.user_achievements (
  user_id        uuid not null references public.profiles (id) on delete cascade,
  achievement_id text not null references public.achievements (id) on delete cascade,
  unlocked_at    timestamptz not null default now(),
  primary key (user_id, achievement_id)
);

-- ───────────────────────── Друзья ─────────────────────────
-- Одна строка на заявку: requester → addressee. accepted = дружба взаимная.
create table if not exists public.friendships (
  requester  uuid not null references public.profiles (id) on delete cascade,
  addressee  uuid not null references public.profiles (id) on delete cascade,
  accepted   boolean not null default false,
  created_at timestamptz not null default now(),
  primary key (requester, addressee),
  check (requester <> addressee)
);
create index if not exists friendships_addressee on public.friendships (addressee);

-- ───────────────────────── Оценки и отзывы ─────────────────────────
create table if not exists public.reviews (
  user_id    uuid not null references public.profiles (id) on delete cascade,
  anime_id   bigint not null check (anime_id > 0),
  score      integer not null check (score between 1 and 10),
  body       text not null default '' check (char_length(body) <= 1000),
  title      text not null default '' check (char_length(title) <= 200),
  poster     text not null default '' check (poster = '' or (poster ~ '^https://' and char_length(poster) <= 500)),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (user_id, anime_id)
);
create index if not exists reviews_anime on public.reviews (anime_id, updated_at desc);

-- ───────────────────────── Права ─────────────────────────
alter table public.anime_lists       enable row level security;
alter table public.achievements      enable row level security;
alter table public.user_achievements enable row level security;
alter table public.friendships       enable row level security;
alter table public.reviews           enable row level security;

drop policy if exists "lists are public" on public.anime_lists;
create policy "lists are public" on public.anime_lists for select using (true);
drop policy if exists "achievements are public" on public.achievements;
create policy "achievements are public" on public.achievements for select using (true);
drop policy if exists "unlocked are public" on public.user_achievements;
create policy "unlocked are public" on public.user_achievements for select using (true);
drop policy if exists "own friendships" on public.friendships;
create policy "own friendships" on public.friendships for select using (auth.uid() in (requester, addressee));
drop policy if exists "reviews are public" on public.reviews;
create policy "reviews are public" on public.reviews for select using (true);

revoke all on public.anime_lists, public.achievements, public.user_achievements, public.friendships, public.reviews from anon, authenticated;
grant select on public.anime_lists, public.achievements, public.user_achievements, public.reviews to anon, authenticated;
grant select on public.friendships to authenticated;

-- ───────────────────────── Достижения: проверка и выдача ─────────────────────────
-- Возвращает массив только что открытых достижений [{id, title, glyph, points, reward}].
create or replace function public.grant_achievements(p_user uuid, p_night boolean default false)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  pr public.profiles;
  unlocked jsonb := '[]'::jsonb;
  a public.achievements;
  ok boolean;
  completed integer; reviews_count integer; friends integer; owned integer;
begin
  select * into pr from profiles where id = p_user;
  if not found then return unlocked; end if;
  select count(*) into completed from anime_lists where user_id = p_user and status = 'completed';
  select count(*) into reviews_count from reviews where user_id = p_user;
  select count(*) into friends from friendships where accepted and p_user in (requester, addressee);
  select count(*) into owned from inventory where user_id = p_user;
  for a in select * from achievements where id not in (select achievement_id from user_achievements where user_id = p_user) order by sort loop
    ok := case a.id
      when 'first_episode' then pr.episodes_watched >= 1
      when 'episodes_10'   then pr.episodes_watched >= 10
      when 'episodes_50'   then pr.episodes_watched >= 50
      when 'episodes_200'  then pr.episodes_watched >= 200
      when 'night_owl'     then p_night
      when 'finisher'      then completed >= 1
      when 'finisher_10'   then completed >= 10
      when 'streak_3'      then pr.streak_days >= 3
      when 'streak_7'      then pr.streak_days >= 7
      when 'streak_30'     then pr.streak_days >= 30
      when 'critic'        then reviews_count >= 1
      when 'critic_10'     then reviews_count >= 10
      when 'friend'        then friends >= 1
      when 'friends_5'     then friends >= 5
      when 'collector'     then owned >= 5
      else false end;
    if ok then
      insert into user_achievements (user_id, achievement_id) values (p_user, a.id) on conflict do nothing;
      if a.points > 0 then
        update profiles set points = points + a.points, total_earned = total_earned + a.points where id = p_user;
      end if;
      if a.reward is not null then
        insert into inventory (user_id, decoration_id) values (p_user, a.reward) on conflict do nothing;
      end if;
      unlocked := unlocked || jsonb_build_object('id', a.id, 'title', a.title, 'glyph', a.glyph, 'points', a.points, 'reward', a.reward);
    end if;
  end loop;
  return unlocked;
end $$;
revoke execute on function public.grant_achievements(uuid, boolean) from public, anon, authenticated;

-- ───────────────────────── Начисление за серию: streak, списки, достижения ─────────────────────────
-- Новые параметры необязательны, поэтому старые клиенты продолжают работать.
-- p_tz_minutes — смещение часового пояса клиента (Минск = 180): для дня streak и «ночной совы».
drop function if exists public.award_watch(bigint, bigint, text, integer);
create or replace function public.award_watch(p_anime_id bigint, p_video_id bigint, p_episode text, p_seconds integer,
  p_title text default null, p_poster text default null, p_total integer default null, p_tz_minutes integer default 180)
returns jsonb language plpgsql security definer set search_path = public as $$
declare
  me uuid := auth.uid();
  base_points constant integer := 10;
  new_anime_bonus constant integer := 5;
  daily_limit constant integer := 40;
  cooldown constant interval := interval '4 minutes';
  local_now timestamp := (now() at time zone 'UTC') + make_interval(mins => greatest(-840, least(840, coalesce(p_tz_minutes, 180))));
  today date := local_now::date;
  gained integer; streak_bonus integer := 0;
  last_award timestamptz; today_count integer; counted integer;
  p public.profiles;
  unlocked jsonb;
  list_status text;
  clean_poster text := case when coalesce(p_poster, '') ~ '^https://' and char_length(p_poster) <= 500 then p_poster else '' end;
begin
  if me is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  if p_anime_id is null or p_anime_id <= 0 or coalesce(p_episode, '') = '' or char_length(p_episode) > 16 then
    raise exception 'bad_episode' using errcode = '22023';
  end if;
  select * into p from profiles where id = me for update;
  if not found then raise exception 'no_profile' using errcode = 'P0002'; end if;
  if coalesce(p_seconds, 0) < 300 then
    return jsonb_build_object('awarded', false, 'reason', 'too_short', 'points', 0, 'balance', p.points, 'total', p.total_earned, 'level', yoru_level(p.total_earned), 'streak', p.streak_days, 'achievements', '[]'::jsonb);
  end if;
  if exists (select 1 from watch_awards where user_id = me and anime_id = p_anime_id and episode = p_episode) then
    return jsonb_build_object('awarded', false, 'reason', 'already', 'points', 0, 'balance', p.points, 'total', p.total_earned, 'level', yoru_level(p.total_earned), 'streak', p.streak_days, 'achievements', '[]'::jsonb);
  end if;
  select max(awarded_at) into last_award from watch_awards where user_id = me;
  if last_award is not null and now() - last_award < cooldown then
    return jsonb_build_object('awarded', false, 'reason', 'cooldown', 'points', 0, 'balance', p.points, 'total', p.total_earned, 'level', yoru_level(p.total_earned), 'streak', p.streak_days, 'achievements', '[]'::jsonb);
  end if;
  select count(*) into today_count from watch_awards where user_id = me and awarded_at > now() - interval '24 hours';
  if today_count >= daily_limit then
    return jsonb_build_object('awarded', false, 'reason', 'daily_limit', 'points', 0, 'balance', p.points, 'total', p.total_earned, 'level', yoru_level(p.total_earned), 'streak', p.streak_days, 'achievements', '[]'::jsonb);
  end if;

  gained := base_points;
  if not exists (select 1 from watch_awards where user_id = me and anime_id = p_anime_id) then
    gained := gained + new_anime_bonus;
  end if;
  -- Streak: первый зачёт за день продлевает серию дней; бонус 2 очка за каждый день подряд, максимум 20.
  if p.last_watch_date is distinct from today then
    if p.last_watch_date = today - 1 then p.streak_days := p.streak_days + 1; else p.streak_days := 1; end if;
    streak_bonus := least(p.streak_days, 10) * 2;
    if p.streak_days = 1 then streak_bonus := 0; end if;
  end if;
  insert into watch_awards (user_id, anime_id, episode, video_id, seconds, points)
  values (me, p_anime_id, p_episode, coalesce(p_video_id, 0), least(p_seconds, 86400), gained + streak_bonus);
  update profiles set points = points + gained + streak_bonus, total_earned = total_earned + gained + streak_bonus,
    episodes_watched = episodes_watched + 1, streak_days = p.streak_days, streak_best = greatest(streak_best, p.streak_days),
    last_watch_date = today
  where id = me;

  -- Список: аниме попадает в «Смотрю», а после последней серии — в «Просмотрено».
  select count(*) into counted from watch_awards where user_id = me and anime_id = p_anime_id;
  list_status := case when coalesce(p_total, 0) > 0 and counted >= p_total then 'completed' else 'watching' end;
  insert into anime_lists (user_id, anime_id, status, title, poster, episodes, total)
  values (me, p_anime_id, list_status, left(coalesce(p_title, ''), 200), clean_poster, counted, greatest(coalesce(p_total, 0), 0))
  on conflict (user_id, anime_id) do update set
    status = case when anime_lists.status = 'completed' or excluded.status = 'completed' then 'completed' else 'watching' end,
    title = coalesce(nullif(excluded.title, ''), anime_lists.title),
    poster = coalesce(nullif(excluded.poster, ''), anime_lists.poster),
    episodes = excluded.episodes, total = greatest(anime_lists.total, excluded.total), updated_at = now();

  unlocked := grant_achievements(me, extract(hour from local_now) < 5);
  select * into p from profiles where id = me;
  return jsonb_build_object('awarded', true, 'reason', 'ok', 'points', gained + streak_bonus, 'streak_bonus', streak_bonus,
    'balance', p.points, 'total', p.total_earned, 'level', yoru_level(p.total_earned), 'streak', p.streak_days,
    'list', list_status, 'achievements', unlocked);
end $$;

-- ───────────────────────── Списки ─────────────────────────
-- p_status = '' удаляет аниме из списков.
create or replace function public.set_list_status(p_anime_id bigint, p_status text, p_title text default '', p_poster text default '', p_total integer default 0)
returns jsonb language plpgsql security definer set search_path = public as $$
declare me uuid := auth.uid(); unlocked jsonb;
begin
  if me is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  if coalesce(p_status, '') = '' then
    delete from anime_lists where user_id = me and anime_id = p_anime_id;
    return jsonb_build_object('status', '', 'achievements', '[]'::jsonb);
  end if;
  if p_status not in ('watching', 'planned', 'completed', 'dropped') then raise exception 'bad_status' using errcode = '22023'; end if;
  insert into anime_lists (user_id, anime_id, status, title, poster, total)
  values (me, p_anime_id, p_status, left(coalesce(p_title, ''), 200),
          case when coalesce(p_poster, '') ~ '^https://' and char_length(p_poster) <= 500 then p_poster else '' end, greatest(coalesce(p_total, 0), 0))
  on conflict (user_id, anime_id) do update set status = excluded.status,
    title = coalesce(nullif(excluded.title, ''), anime_lists.title), poster = coalesce(nullif(excluded.poster, ''), anime_lists.poster),
    total = greatest(anime_lists.total, excluded.total), updated_at = now();
  unlocked := grant_achievements(me);
  return jsonb_build_object('status', p_status, 'achievements', unlocked);
end $$;

-- ───────────────────────── Отзывы ─────────────────────────
-- p_score = 0 удаляет отзыв.
create or replace function public.set_review(p_anime_id bigint, p_score integer, p_body text default '', p_title text default '', p_poster text default '')
returns jsonb language plpgsql security definer set search_path = public as $$
declare me uuid := auth.uid(); unlocked jsonb;
begin
  if me is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  if coalesce(p_score, 0) = 0 then
    delete from reviews where user_id = me and anime_id = p_anime_id;
    return jsonb_build_object('deleted', true, 'achievements', '[]'::jsonb);
  end if;
  if p_score not between 1 and 10 then raise exception 'bad_score' using errcode = '22023'; end if;
  insert into reviews (user_id, anime_id, score, body, title, poster)
  values (me, p_anime_id, p_score, left(btrim(coalesce(p_body, '')), 1000), left(coalesce(p_title, ''), 200),
          case when coalesce(p_poster, '') ~ '^https://' and char_length(p_poster) <= 500 then p_poster else '' end)
  on conflict (user_id, anime_id) do update set score = excluded.score, body = excluded.body,
    title = coalesce(nullif(excluded.title, ''), reviews.title), poster = coalesce(nullif(excluded.poster, ''), reviews.poster), updated_at = now();
  unlocked := grant_achievements(me);
  return jsonb_build_object('deleted', false, 'achievements', unlocked);
end $$;

-- Средняя оценка пользователей Yoru.
create or replace function public.anime_rating(p_anime_id bigint)
returns jsonb language sql stable security invoker set search_path = public as $$
  select jsonb_build_object('average', coalesce(round(avg(score)::numeric, 1), 0), 'count', count(*)) from reviews where anime_id = p_anime_id;
$$;

-- ───────────────────────── Друзья ─────────────────────────
-- Заявка по нику. Если встречная заявка уже есть — дружба подтверждается сразу.
create or replace function public.send_friend_request(p_username text)
returns jsonb language plpgsql security definer set search_path = public as $$
declare me uuid := auth.uid(); other uuid; state text; unlocked jsonb := '[]'::jsonb;
begin
  if me is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  select id into other from profiles where username = lower(btrim(p_username));
  if other is null then raise exception 'user_not_found' using errcode = 'P0002'; end if;
  if other = me then raise exception 'self_friend' using errcode = '22023'; end if;
  if exists (select 1 from friendships where requester = other and addressee = me) then
    update friendships set accepted = true where requester = other and addressee = me;
    state := 'accepted';
    unlocked := grant_achievements(me); perform grant_achievements(other);
  elsif exists (select 1 from friendships where requester = me and addressee = other) then
    select case when accepted then 'accepted' else 'pending' end into state from friendships where requester = me and addressee = other;
  else
    if (select count(*) from friendships where requester = me and not accepted) >= 50 then raise exception 'too_many_requests' using errcode = '54000'; end if;
    insert into friendships (requester, addressee) values (me, other);
    state := 'pending';
  end if;
  return jsonb_build_object('state', state, 'achievements', unlocked);
end $$;

create or replace function public.respond_friend_request(p_requester uuid, p_accept boolean)
returns jsonb language plpgsql security definer set search_path = public as $$
declare me uuid := auth.uid(); unlocked jsonb := '[]'::jsonb;
begin
  if me is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  if p_accept then
    update friendships set accepted = true where requester = p_requester and addressee = me;
    if not found then raise exception 'request_not_found' using errcode = 'P0002'; end if;
    unlocked := grant_achievements(me); perform grant_achievements(p_requester);
  else
    delete from friendships where requester = p_requester and addressee = me;
  end if;
  return jsonb_build_object('accepted', p_accept, 'achievements', unlocked);
end $$;

create or replace function public.remove_friend(p_user uuid)
returns void language sql security definer set search_path = public as $$
  delete from friendships where (requester = auth.uid() and addressee = p_user) or (requester = p_user and addressee = auth.uid());
$$;

-- Друзья, входящие и исходящие заявки одним списком: [{id, username, display_name, avatar_url, equipped_*, points, level, state}].
-- state: friend, incoming, outgoing.
create or replace function public.my_friends()
returns jsonb language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object(
    'id', pr.id, 'username', pr.username, 'display_name', pr.display_name, 'avatar_url', pr.avatar_url, 'status', pr.status,
    'equipped_frame', pr.equipped_frame, 'equipped_badge', pr.equipped_badge, 'equipped_color', pr.equipped_color,
    'level', yoru_level(pr.total_earned), 'streak_days', pr.streak_days,
    'state', case when f.accepted then 'friend' when f.addressee = auth.uid() then 'incoming' else 'outgoing' end
  ) order by f.accepted, pr.username), '[]'::jsonb)
  from friendships f join profiles pr on pr.id = case when f.requester = auth.uid() then f.addressee else f.requester end
  where auth.uid() in (f.requester, f.addressee);
$$;

-- Лента: что недавно смотрели друзья (до 40 записей).
create or replace function public.friend_feed()
returns jsonb language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(item order by (item->>'updated_at') desc), '[]'::jsonb) from (
    select jsonb_build_object('username', pr.username, 'display_name', pr.display_name, 'equipped_color', pr.equipped_color,
      'anime_id', l.anime_id, 'title', l.title, 'poster', l.poster, 'status', l.status, 'episodes', l.episodes, 'total', l.total,
      'updated_at', l.updated_at) as item
    from anime_lists l join profiles pr on pr.id = l.user_id
    where l.user_id in (select case when requester = auth.uid() then addressee else requester end from friendships
                        where accepted and auth.uid() in (requester, addressee))
    order by l.updated_at desc limit 40) feed;
$$;

-- Публичный профиль по нику: профиль, достижения, списки по статусам и состояние дружбы со мной.
create or replace function public.public_profile(p_username text)
returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'profile', jsonb_build_object('id', pr.id, 'username', pr.username, 'display_name', pr.display_name, 'status', pr.status,
      'avatar_url', pr.avatar_url, 'showcase', pr.showcase, 'equipped_frame', pr.equipped_frame, 'equipped_badge', pr.equipped_badge,
      'equipped_color', pr.equipped_color, 'total_earned', pr.total_earned, 'episodes_watched', pr.episodes_watched,
      'streak_days', pr.streak_days, 'streak_best', pr.streak_best, 'created_at', pr.created_at),
    'achievements', coalesce((select jsonb_agg(achievement_id) from user_achievements where user_id = pr.id), '[]'::jsonb),
    'lists', coalesce((select jsonb_object_agg(status, n) from (select status, count(*) n from anime_lists where user_id = pr.id group by status) s), '{}'::jsonb),
    'reviews', (select count(*) from reviews where user_id = pr.id),
    'friendship', coalesce((select case when accepted then 'friend' when requester = auth.uid() then 'outgoing' else 'incoming' end
      from friendships where (requester = auth.uid() and addressee = pr.id) or (requester = pr.id and addressee = auth.uid())), case when pr.id = auth.uid() then 'self' else 'none' end))
  from profiles pr where pr.username = lower(btrim(p_username));
$$;

-- ───────────────────────── Покупка и надевание с учётом новых правил ─────────────────────────
create or replace function public.buy_decoration(p_id text)
returns public.profiles language plpgsql security definer set search_path = public as $$
declare me uuid := auth.uid(); d public.decorations; p public.profiles; unlocked jsonb;
begin
  if me is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  select * into d from decorations where id = p_id and active;
  if not found then raise exception 'unknown_decoration' using errcode = 'P0002'; end if;
  select * into p from profiles where id = me for update;
  if exists (select 1 from inventory where user_id = me and decoration_id = d.id) or (d.price = 0 and not d.exclusive) then
    raise exception 'already_owned' using errcode = '23505';
  end if;
  if d.exclusive then raise exception 'achievement_only' using errcode = '42501'; end if;
  if (d.available_from is not null and now() < d.available_from) or (d.available_until is not null and now() > d.available_until) then
    raise exception 'not_available' using errcode = '42501';
  end if;
  if yoru_level(p.total_earned) < d.min_level then raise exception 'level_too_low' using errcode = '42501'; end if;
  if p.points < d.price then raise exception 'not_enough_points' using errcode = '42501'; end if;
  insert into inventory (user_id, decoration_id) values (me, d.id);
  update profiles set points = points - d.price where id = me;
  unlocked := grant_achievements(me);
  select * into p from profiles where id = me;
  return p;
end $$;

create or replace function public.equip_decoration(p_kind text, p_id text)
returns public.profiles language plpgsql security definer set search_path = public as $$
declare me uuid := auth.uid(); d public.decorations; p public.profiles;
begin
  if me is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  if p_kind not in ('frame', 'badge', 'name_color') then raise exception 'bad_kind' using errcode = '22023'; end if;
  if coalesce(p_id, '') <> '' then
    select * into d from decorations where id = p_id and kind = p_kind;
    if not found then raise exception 'unknown_decoration' using errcode = 'P0002'; end if;
    if (d.price > 0 or d.exclusive) and not exists (select 1 from inventory where user_id = me and decoration_id = d.id) then
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

-- ───────────────────────── Права на функции ─────────────────────────
revoke execute on function public.award_watch(bigint, bigint, text, integer, text, text, integer, integer), public.set_list_status(bigint, text, text, text, integer),
  public.set_review(bigint, integer, text, text, text), public.send_friend_request(text), public.respond_friend_request(uuid, boolean),
  public.remove_friend(uuid), public.my_friends(), public.friend_feed(), public.buy_decoration(text), public.equip_decoration(text, text) from public, anon;
grant execute on function public.award_watch(bigint, bigint, text, integer, text, text, integer, integer), public.set_list_status(bigint, text, text, text, integer),
  public.set_review(bigint, integer, text, text, text), public.send_friend_request(text), public.respond_friend_request(uuid, boolean),
  public.remove_friend(uuid), public.my_friends(), public.friend_feed(), public.buy_decoration(text), public.equip_decoration(text, text) to authenticated;
revoke execute on function public.public_profile(text), public.anime_rating(bigint) from public;
grant execute on function public.public_profile(text), public.anime_rating(bigint) to anon, authenticated;

-- ───────────────────────── Каталог: эксклюзивные и сезонные украшения ─────────────────────────
insert into public.decorations (id, kind, title, description, price, min_level, rarity, style, sort, exclusive, available_from, available_until) values
  -- За достижения (не продаются).
  ('frame_nightowl',  'frame', 'Ночная сова',     'За просмотр после полуночи',      0, 1, 'epic',      '{"colors":["#2D2843","#6FC3FF","#2D2843","#BFAEFF"],"animated":true}', 100, true, null, null),
  ('frame_flame',     'frame', 'Неугасимый',      'За 7 дней подряд',                0, 1, 'epic',      '{"colors":["#FFE08A","#FF6A3D","#C2185B","#FF6A3D"],"animated":true,"glow":true}', 110, true, null, null),
  ('badge_finisher',  'badge', 'Финалист',        'За досмотренное аниме',           0, 1, 'rare',      '{"glyph":"✔","color":"#7EDCA9"}', 100, true, null, null),
  ('badge_critic',    'badge', 'Критик',          'За 10 отзывов',                   0, 1, 'epic',      '{"glyph":"✎","color":"#FFC768"}', 110, true, null, null),
  ('badge_heart',     'badge', 'Душа компании',   'За 5 друзей',                     0, 1, 'epic',      '{"glyph":"♥","color":"#FF8FB8"}', 120, true, null, null),
  ('color_legend',    'name_color', 'Легенда',    'За 200 серий',                    0, 1, 'legendary', '{"colors":["#FFC768","#FF8FB8","#BFAEFF","#6FC3FF"]}', 100, true, null, null),
  -- Хэллоуин 2026.
  ('frame_pumpkin',   'frame', 'Тыквенная ночь',  'Хэллоуин · только до 3 ноября',   200, 1, 'rare',      '{"colors":["#FF8A1F","#2B1A3A","#FF8A1F"],"glow":true}', 200, false, '2026-10-24', '2026-11-04'),
  ('badge_pumpkin',   'badge', 'Тыква',           'Хэллоуин · только до 3 ноября',   120, 1, 'rare',      '{"glyph":"🎃","color":"#FF8A1F"}', 200, false, '2026-10-24', '2026-11-04'),
  ('color_spooky',    'name_color', 'Жуть',       'Хэллоуин · только до 3 ноября',   150, 1, 'rare',      '{"colors":["#FF8A1F","#B388FF"]}', 200, false, '2026-10-24', '2026-11-04'),
  -- Новый год 2027.
  ('frame_frost',     'frame', 'Иней',            'Новый год · 20 декабря — 14 января', 300, 1, 'epic',   '{"colors":["#E8F6FF","#8FD3FF","#FFFFFF","#8FD3FF"],"animated":true,"glow":true}', 210, false, '2026-12-20', '2027-01-15'),
  ('badge_snow',      'badge', 'Снежинка',        'Новый год · 20 декабря — 14 января', 150, 1, 'rare',   '{"glyph":"❄","color":"#8FD3FF"}', 210, false, '2026-12-20', '2027-01-15'),
  ('color_frost',     'name_color', 'Мороз',      'Новый год · 20 декабря — 14 января', 200, 1, 'rare',   '{"colors":["#FFFFFF","#8FD3FF"]}', 210, false, '2026-12-20', '2027-01-15')
on conflict (id) do update set
  kind = excluded.kind, title = excluded.title, description = excluded.description, price = excluded.price, min_level = excluded.min_level,
  rarity = excluded.rarity, style = excluded.style, sort = excluded.sort, exclusive = excluded.exclusive,
  available_from = excluded.available_from, available_until = excluded.available_until;

insert into public.achievements (id, title, description, glyph, points, reward, sort) values
  ('first_episode', 'Первый шаг',        'Посмотри первую серию',            '☾', 10,  null,             10),
  ('episodes_10',   'Втянулся',          'Посмотри 10 серий',                '▶', 30,  null,             20),
  ('episodes_50',   'Марафонец',         'Посмотри 50 серий',                '⚡', 100, null,             30),
  ('episodes_200',  'Легенда',           'Посмотри 200 серий',               '♛', 300, 'color_legend',   40),
  ('night_owl',     'Ночная сова',       'Посмотри серию после полуночи',    '☽', 20,  'frame_nightowl', 50),
  ('finisher',      'До финала',         'Досмотри аниме до конца',          '✔', 50,  'badge_finisher', 60),
  ('finisher_10',   'Коллекционер историй', 'Досмотри 10 аниме',             '✪', 200, null,             70),
  ('streak_3',      'Три вечера',        'Смотри 3 дня подряд',              '🔥', 20,  null,             80),
  ('streak_7',      'Неделя без пропусков', 'Смотри 7 дней подряд',          '🔥', 70,  'frame_flame',    90),
  ('streak_30',     'Месяц аниме',       'Смотри 30 дней подряд',            '🔥', 300, null,             100),
  ('critic',        'Первое мнение',     'Напиши отзыв',                     '✎', 15,  null,             110),
  ('critic_10',     'Критик',            'Напиши 10 отзывов',                '✎', 100, 'badge_critic',   120),
  ('friend',        'Не один',           'Добавь первого друга',             '♥', 15,  null,             130),
  ('friends_5',     'Душа компании',     'Собери 5 друзей',                  '♥', 60,  'badge_heart',    140),
  ('collector',     'Модник',            'Собери 5 украшений',               '✦', 50,  null,             150)
on conflict (id) do update set title = excluded.title, description = excluded.description, glyph = excluded.glyph,
  points = excluded.points, reward = excluded.reward, sort = excluded.sort;

-- Yoru 0.11: синхронизация просмотра между устройствами.
-- Одна строка на (пользователь, аниме): последняя серия и позиция в ней, выбранная озвучка и плеер
-- (это же — «любимая озвучка» тайтла) и номера просмотренных серий.
-- Применяется после 20261101000000_party_stats.sql.

create table if not exists public.watch_progress (
  user_id    uuid not null references public.profiles(id) on delete cascade,
  anime_id   bigint not null check (anime_id > 0),
  episode    text not null default '' check (char_length(episode) <= 16),
  dubbing    text not null default '' check (char_length(dubbing) <= 120),
  player     text not null default '' check (char_length(player) <= 60),
  position   real not null default 0 check (position >= 0 and position < 86400),
  duration   real not null default 0 check (duration >= 0 and duration < 86400),
  watched    text[] not null default '{}' check (cardinality(watched) <= 5000),
  title      text not null default '' check (char_length(title) <= 200),
  poster     text not null default '' check (poster = '' or (poster ~ '^https://' and char_length(poster) <= 500)),
  updated_at timestamptz not null default clock_timestamp(),
  primary key (user_id, anime_id)
);
create index if not exists watch_progress_recent on public.watch_progress (user_id, updated_at desc);
alter table public.watch_progress enable row level security;
drop policy if exists "own progress" on public.watch_progress;
create policy "own progress" on public.watch_progress for select using (user_id = auth.uid());

create or replace function public.progress_json(w public.watch_progress) returns jsonb language sql immutable as $$
  select jsonb_build_object('anime_id', w.anime_id, 'episode', w.episode, 'dubbing', w.dubbing, 'player', w.player,
    'position', w.position, 'duration', w.duration, 'watched', to_jsonb(w.watched), 'title', w.title, 'poster', w.poster,
    'updated_at', w.updated_at)
$$;

-- Сохраняет текущую серию, позицию и озвучку. Пустые озвучка/плеер не затирают сохранённые.
create or replace function public.save_progress(p_anime_id bigint, p_episode text, p_dubbing text, p_player text,
  p_position real, p_duration real, p_title text default '', p_poster text default '')
returns jsonb language plpgsql security definer set search_path = public as $$
declare me uuid := auth.uid(); w watch_progress;
begin
  if me is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  if p_anime_id is null or p_anime_id <= 0 or char_length(coalesce(p_episode, '')) > 16 or coalesce(p_position, 0) < 0 or coalesce(p_duration, 0) < 0 then raise exception 'bad_progress' using errcode = '22023'; end if;
  insert into watch_progress (user_id, anime_id, episode, dubbing, player, position, duration, title, poster)
  values (me, p_anime_id, coalesce(p_episode, ''), left(coalesce(p_dubbing, ''), 120), left(coalesce(p_player, ''), 60),
    least(greatest(coalesce(p_position, 0), 0), 86399), least(greatest(coalesce(p_duration, 0), 0), 86399),
    left(coalesce(p_title, ''), 200), clean_poster(p_poster))
  on conflict (user_id, anime_id) do update set
    episode = case when excluded.episode = '' then watch_progress.episode else excluded.episode end,
    dubbing = case when excluded.dubbing = '' then watch_progress.dubbing else excluded.dubbing end,
    player = case when excluded.player = '' then watch_progress.player else excluded.player end,
    position = excluded.position, duration = excluded.duration,
    title = case when excluded.title = '' then watch_progress.title else excluded.title end,
    poster = case when excluded.poster = '' then watch_progress.poster else excluded.poster end,
    updated_at = clock_timestamp()
  returning * into w;
  return progress_json(w);
end $$;

-- Отмечает серию просмотренной (или снимает отметку).
create or replace function public.set_watched(p_anime_id bigint, p_episode text, p_watched boolean)
returns jsonb language plpgsql security definer set search_path = public as $$
declare me uuid := auth.uid(); w watch_progress;
begin
  if me is null then raise exception 'not_authenticated' using errcode = '28000'; end if;
  if p_anime_id is null or p_anime_id <= 0 or coalesce(p_episode, '') = '' or char_length(p_episode) > 16 then raise exception 'bad_progress' using errcode = '22023'; end if;
  insert into watch_progress (user_id, anime_id) values (me, p_anime_id) on conflict do nothing;
  update watch_progress set
    watched = case when p_watched then (select coalesce(array_agg(distinct e), '{}') from unnest(array_append(watched, p_episode)) e)
                   else array_remove(watched, p_episode) end,
    updated_at = clock_timestamp()
  where user_id = me and anime_id = p_anime_id returning * into w;
  if cardinality(w.watched) > 5000 then raise exception 'too_many' using errcode = '22023'; end if;
  return progress_json(w);
end $$;

-- Прогресс по одному аниме (или null).
create or replace function public.anime_progress(p_anime_id bigint)
returns jsonb language sql stable security definer set search_path = public as $$
  select progress_json(w) from watch_progress w where w.user_id = auth.uid() and w.anime_id = p_anime_id
$$;

-- Прогресс по всем аниме, изменённый после p_since (для синхронизации), новые первыми.
create or replace function public.my_progress(p_since timestamptz default null, p_limit integer default 300)
returns jsonb language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(progress_json(w) order by w.updated_at desc), '[]'::jsonb) from (
    select * from watch_progress w where w.user_id = auth.uid() and (p_since is null or w.updated_at > p_since)
    order by w.updated_at desc limit least(greatest(coalesce(p_limit, 300), 1), 1000)) w
$$;

revoke all on public.watch_progress from anon, authenticated;
grant select on public.watch_progress to authenticated;
revoke execute on function public.progress_json(public.watch_progress) from public, anon, authenticated;
revoke execute on function public.save_progress(bigint, text, text, text, real, real, text, text), public.set_watched(bigint, text, boolean),
  public.anime_progress(bigint), public.my_progress(timestamptz, integer) from public, anon;
grant execute on function public.save_progress(bigint, text, text, text, real, real, text, text), public.set_watched(bigint, text, boolean),
  public.anime_progress(bigint), public.my_progress(timestamptz, integer) to authenticated;

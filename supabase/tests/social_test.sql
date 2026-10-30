-- Проверки списков, достижений, streak, сезонных украшений, друзей и отзывов (после обеих миграций).
\set ON_ERROR_STOP 1
insert into auth.users(id, raw_user_meta_data) values
 ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa','{"username":"alice"}'),
 ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb','{"username":"bob"}');

create or replace function pg_temp.expect_error(statement text, expected text) returns void language plpgsql as $$
begin
  execute statement;
  raise exception 'expected error "%" for: %', expected, statement;
exception when others then
  if sqlerrm not like '%' || expected || '%' then raise exception 'expected "%" got "%" for: %', expected, sqlerrm, statement; end if;
end $$;
grant execute on function pg_temp.expect_error(text, text) to anon, authenticated;

set role authenticated;
select set_config('request.jwt.claim.sub', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', false);
do $$ declare r jsonb; begin
  -- Старый клиент (4 параметра) продолжает работать.
  r := award_watch(7, 1, '1', 600);
  assert (r->>'awarded')::bool and (r->>'points')::int = 15 and (r->>'streak')::int = 1, 'first award, streak 1: ' || r;
  assert r->'achievements' @> '[{"id":"first_episode"}]', 'first_episode unlocked';
  assert (r->>'balance')::int = 25, 'achievement points added: ' || r;
  assert (select status from anime_lists where user_id = auth.uid() and anime_id = 7) = 'watching', 'auto list watching';
end $$;
reset role;
-- Вчерашний зачёт → сегодня streak 2 и бонус 4.
update watch_awards set awarded_at = now() - interval '1 day';
update profiles set last_watch_date = last_watch_date - 1 where username = 'alice';
set role authenticated;
do $$ declare r jsonb; begin
  r := award_watch(7, 2, '2', 700, 'Фрирен', 'https://p/f.jpg', 2, 180);
  assert (r->>'streak')::int = 2 and (r->>'streak_bonus')::int = 4 and (r->>'points')::int = 14, 'streak bonus: ' || r;
  assert r->>'list' = 'completed', 'last episode completes list';
  assert r->'achievements' @> '[{"id":"finisher"}]', 'finisher unlocked';
  assert exists (select 1 from inventory where user_id = auth.uid() and decoration_id = 'badge_finisher'), 'exclusive reward granted';
  assert (select title from anime_lists where user_id = auth.uid() and anime_id = 7) = 'Фрирен', 'list title stored';
end $$;
do $$ declare p profiles; begin
  p := equip_decoration('badge', 'badge_finisher'); assert p.equipped_badge = 'badge_finisher', 'exclusive equips';
end $$;
select pg_temp.expect_error($q$select equip_decoration('frame', 'frame_flame')$q$, 'not_owned');
select pg_temp.expect_error($q$select buy_decoration('frame_flame')$q$, 'achievement_only');
reset role; update profiles set points = 1000 where username = 'alice'; set role authenticated;
select pg_temp.expect_error($q$select buy_decoration('frame_frost')$q$, 'not_available');
do $$ declare p profiles; begin
  if now() between '2026-10-24' and '2026-11-04' then p := buy_decoration('frame_pumpkin'); assert p.points = 800, 'seasonal purchase in window'; end if;
end $$;
-- Списки и отзывы.
do $$ declare r jsonb; begin
  r := set_list_status(42, 'planned', 'Ван-Пис', 'https://p/o.jpg', 1100); assert r->>'status' = 'planned', 'planned';
  r := set_list_status(42, 'dropped'); assert (select status from anime_lists where user_id = auth.uid() and anime_id = 42) = 'dropped', 'status change';
  r := set_list_status(42, ''); assert not exists (select 1 from anime_lists where user_id = auth.uid() and anime_id = 42), 'removed';
  r := set_review(7, 9, '  Очень тёплая история  ', 'Фрирен', 'https://p/f.jpg');
  assert r->'achievements' @> '[{"id":"critic"}]', 'critic unlocked';
  assert (select body from reviews where user_id = auth.uid() and anime_id = 7) = 'Очень тёплая история', 'body trimmed';
end $$;
select pg_temp.expect_error($q$select set_review(7, 11)$q$, 'bad_score');
select pg_temp.expect_error($q$select set_list_status(7, 'hacked')$q$, 'bad_status');
select pg_temp.expect_error($q$insert into reviews(user_id, anime_id, score) values (auth.uid(), 1, 5)$q$, 'permission denied');
-- Друзья: alice → bob, bob принимает.
do $$ declare r jsonb; begin
  r := send_friend_request('BOB'); assert r->>'state' = 'pending', 'request pending';
  r := send_friend_request('bob'); assert r->>'state' = 'pending', 'repeat is idempotent';
  assert jsonb_array_length(my_friends()) = 1 and my_friends()->0->>'state' = 'outgoing', 'outgoing listed';
end $$;
select pg_temp.expect_error($q$select send_friend_request('alice')$q$, 'self_friend');
select pg_temp.expect_error($q$select send_friend_request('nobody_here')$q$, 'user_not_found');
select set_config('request.jwt.claim.sub', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', false);
do $$ declare r jsonb; begin
  assert my_friends()->0->>'state' = 'incoming', 'incoming listed';
  r := respond_friend_request('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', true);
  assert r->'achievements' @> '[{"id":"friend"}]', 'friend achievement';
  assert my_friends()->0->>'state' = 'friend', 'now friends';
  assert jsonb_array_length(friend_feed()) = 1 and friend_feed()->0->>'title' = 'Фрирен', 'feed shows friend lists';
  r := public_profile('alice');
  assert r->>'friendship' = 'friend' and r->'lists'->>'completed' = '1' and r->'achievements' @> '["finisher"]', 'public profile: ' || r;
  assert (anime_rating(7)->>'average')::numeric = 9 and (anime_rating(7)->>'count')::int = 1, 'rating';
end $$;
-- Bob не видит чужие дружбы, но видит публичные списки.
reset role;
insert into auth.users(id, raw_user_meta_data) values ('cccccccc-cccc-cccc-cccc-cccccccccccc','{"username":"carol"}');
set role authenticated;
select set_config('request.jwt.claim.sub', 'cccccccc-cccc-cccc-cccc-cccccccccccc', false);
do $$ begin
  assert (select count(*) from friendships) = 0, 'friendships private';
  assert jsonb_array_length(friend_feed()) = 0, 'no feed without friends';
  assert public_profile('alice')->>'friendship' = 'none', 'stranger';
  perform remove_friend('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
end $$;
reset role;
do $$ begin assert (select count(*) from friendships where accepted) = 1, 'stranger cannot remove others friendship'; end $$;
select set_config('request.jwt.claim.sub', '', false);
set role anon;
do $$ begin assert public_profile('alice') is not null and (anime_rating(7)->>'count')::int = 1, 'anon can read public data'; end $$;
select pg_temp.expect_error($q$select my_friends()$q$, 'permission denied');
reset role;
\echo 'All social checks passed.'

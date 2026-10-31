-- Проверки 0.9: украшения как в Discord, совместный просмотр, метки опенинга, комментарии, подарки,
-- рейтинг, статистика, «Год в Yoru», рекомендации и перенос списков (после всех миграций).
\set ON_ERROR_STOP 1
insert into auth.users(id, raw_user_meta_data) values
 ('f1000000-0000-0000-0000-000000000001','{"username":"mika"}'),
 ('f2000000-0000-0000-0000-000000000002','{"username":"ren"}'),
 ('f3000000-0000-0000-0000-000000000003','{"username":"stranger"}');
update profiles set points = 5000, total_earned = 20000 where username in ('mika', 'ren', 'stranger');
insert into friendships (requester, addressee, accepted) values ('f1000000-0000-0000-0000-000000000001', 'f2000000-0000-0000-0000-000000000002', true);

create or replace function pg_temp.expect_error(statement text, expected text) returns void language plpgsql as $$
begin
  execute statement;
  raise exception 'expected error "%" for: %', expected, statement;
exception when others then
  if sqlerrm not like '%' || expected || '%' then raise exception 'expected "%" got "%" for: %', expected, sqlerrm, statement; end if;
end $$;
grant execute on function pg_temp.expect_error(text, text) to anon, authenticated;

set role authenticated;
select set_config('request.jwt.claim.sub', 'f1000000-0000-0000-0000-000000000001', false);
-- Украшения: покупка и надевание новых видов.
do $$ declare p profiles; r jsonb; begin
  p := buy_decoration('deco_cat_ears'); p := buy_decoration('fx_sparkles'); p := buy_decoration('pf_neon');
  p := equip_decoration('avatar_deco', 'deco_cat_ears'); assert p.equipped_deco = 'deco_cat_ears', 'deco equipped';
  p := equip_decoration('profile_effect', 'fx_sparkles'); p := equip_decoration('profile_frame', 'pf_neon');
  r := public_profile('mika')->'profile';
  assert r->>'equipped_effect' = 'fx_sparkles' and r->>'equipped_pframe' = 'pf_neon', 'public profile shows decorations: ' || r::text;
  p := equip_decoration('profile_effect', ''); assert p.equipped_effect = '', 'effect removed';
end $$;
select pg_temp.expect_error($q$select equip_decoration('profile_frame', 'pf_flames')$q$, 'not_owned');
select pg_temp.expect_error($q$select equip_decoration('avatar_deco', 'pf_neon')$q$, 'unknown_decoration');

-- Совместный просмотр.
do $$ declare r jsonb; c text; begin
  r := room_create(10, 'Тест', '//static.yani.tv/p.webp', '1', 5); c := r->>'code';
  assert c ~ '^[A-Z2-9]{6}$' and r->>'poster' = 'https://static.yani.tv/p.webp', 'room created: ' || r::text;
  perform set_config('yoru.room', c, false);
end $$;
select set_config('request.jwt.claim.sub', 'f2000000-0000-0000-0000-000000000002', false);
select pg_temp.expect_error($q$select room_poll(current_setting('yoru.room'))$q$, 'not_in_room');
select pg_temp.expect_error($q$select room_join('ZZZZZZ')$q$, 'room_not_found');
do $$ declare r jsonb; c text := current_setting('yoru.room'); begin
  r := room_join(lower(c)); assert jsonb_array_length(r->'members') = 2, 'two members: ' || r::text;
  r := room_control(c, 125.5, false, '2', 6); assert r->>'episode' = '2' and not (r->>'paused')::boolean and (r->>'position')::float = 125.5, 'controlled';
  perform room_say(c, 'chat', 'Привет!'); perform room_say(c, 'reaction', '🔥');
  r := room_poll(c, 0);
  assert r->>'updated_by' = 'ren' and jsonb_array_length(r->'messages') = 4, 'poll: ' || r::text;
  assert (select count(*) from jsonb_array_elements(room_poll(c, (r->'messages'->2->>'id')::bigint)->'messages')) = 1, 'since filter';
end $$;
select pg_temp.expect_error($q$select room_say(current_setting('yoru.room'), 'reaction', 'x')$q$, 'bad_reaction');
select room_leave(current_setting('yoru.room'));
select pg_temp.expect_error($q$select room_control(current_setting('yoru.room'), 1, true)$q$, 'not_in_room');

-- Метки опенинга: медиана, запасной вариант по другим сериям.
do $$ declare r jsonb; begin
  r := mark_intro(10, '1', 'opening', 80, 170); assert (r->'opening'->>'start')::int = 80, 'own mark';
end $$;
select set_config('request.jwt.claim.sub', 'f1000000-0000-0000-0000-000000000001', false);
do $$ declare r jsonb; begin
  r := mark_intro(10, '1', 'opening', 90, 180); r := mark_intro(10, '2', 'ending', 1300, 1390);
  r := anime_intro(10, '1'); assert (r->'opening'->>'votes')::int = 2 and (r->'opening'->>'exact')::boolean, 'median: ' || r::text;
  r := anime_intro(10, '3'); assert not (r->'opening'->>'exact')::boolean and r->'ending' is null, 'fallback: ' || r::text;
end $$;
select pg_temp.expect_error($q$select mark_intro(10, '1', 'opening', 100, 50)$q$, 'bad_range');

-- Комментарии и реакции.
do $$ declare id1 bigint; c jsonb; begin
  id1 := post_comment(10, '1', '  Этот момент!  ', 95);
  perform post_comment(10, '1', 'Без таймкода');
  assert react_comment(id1, '🔥') and not react_comment(id1, '🔥') and react_comment(id1, '❤'), 'toggle reaction';
  c := episode_comments(10, '1');
  assert jsonb_array_length(c) = 2 and c->0->>'body' = 'Этот момент!' and (c->0->'reactions'->>'❤')::int = 1 and (c->0->>'mine')::boolean, 'comments: ' || c::text;
  perform set_config('yoru.comment', id1::text, false);
end $$;
select pg_temp.expect_error($q$select post_comment(10, '1', '   ')$q$, 'empty_comment');
select pg_temp.expect_error($q$select react_comment(current_setting('yoru.comment')::bigint, '💩')$q$, 'comment_reactions_emoji_check');
select set_config('request.jwt.claim.sub', 'f3000000-0000-0000-0000-000000000003', false);
select pg_temp.expect_error($q$select delete_comment(current_setting('yoru.comment')::bigint)$q$, 'not_owner');

-- Подарки только друзьям.
select pg_temp.expect_error($q$select gift_points('f1000000-0000-0000-0000-000000000001', 50)$q$, 'not_friends');
select set_config('request.jwt.claim.sub', 'f1000000-0000-0000-0000-000000000001', false);
do $$ declare r jsonb; g jsonb; begin
  r := gift_points('f2000000-0000-0000-0000-000000000002', 100, 'на тыкву');
  assert (r->>'balance')::int = (select points from profiles where username = 'mika'), 'balance returned';
  r := gift_decoration('f2000000-0000-0000-0000-000000000002', 'deco_halo', 'с праздником');
  assert exists (select 1 from inventory i join profiles p on p.id = i.user_id where p.username = 'ren' and i.decoration_id = 'deco_halo'), 'gifted decoration owned';
end $$;
select pg_temp.expect_error($q$select gift_points('f2000000-0000-0000-0000-000000000002', 5)$q$, 'bad_amount');
select pg_temp.expect_error($q$select gift_points('f2000000-0000-0000-0000-000000000002', 500)$q$, 'gift_limit');
select pg_temp.expect_error($q$select gift_decoration('f2000000-0000-0000-0000-000000000002', 'deco_halo')$q$, 'already_owned');
select pg_temp.expect_error($q$select gift_decoration('f2000000-0000-0000-0000-000000000002', 'frame_nightowl')$q$, 'not_giftable');
select set_config('request.jwt.claim.sub', 'f2000000-0000-0000-0000-000000000002', false);
do $$ declare g jsonb; begin
  g := my_gifts(); assert jsonb_array_length(g) = 2 and not (g->0->>'seen')::boolean and g->1->>'username' = 'mika', 'gifts: ' || g::text;
  perform mark_gifts_seen(); assert (my_gifts()->0->>'seen')::boolean, 'seen';
  assert (select points from profiles where username = 'ren') = 5100 and (select total_earned from profiles where username = 'ren') = 20000, 'gift does not add level';
end $$;

-- Перенос списков со старого сайта.
do $$ declare r jsonb; begin
  r := import_lists('[{"anime_id":1512,"status":"watching","title":"Ван-Пис","poster":"//static.yani.tv/posters/big/1.webp","genres":["Приключения","Экшен"],"score":9},
    {"anime_id":21748,"status":"completed","title":"Киберпанк","genres":["Экшен","Киберпанк"],"favorite":true},
    {"anime_id":30,"status":"on_hold","title":"Отложенное"},
    {"anime_id":0,"status":"watching"},{"anime_id":5,"status":"weird"}]');
  assert (r->>'imported')::int = 3 and (r->>'skipped')::int = 2 and (r->>'rated')::int = 1, 'import: ' || r::text;
  r := import_lists('[{"anime_id":1512,"status":"dropped","score":3}]'); assert (r->>'imported')::int = 0 and (r->>'rated')::int = 0, 'no overwrite: ' || r::text;
  assert (select l.status from anime_lists l join profiles p on p.id = l.user_id where p.username = 'ren' and anime_id = 1512) = 'watching', 'kept status';
  assert (select poster from anime_meta where anime_id = 1512) = 'https://static.yani.tv/posters/big/1.webp', 'poster normalized';
  assert jsonb_array_length(public_profile('ren')->'favorites') = 1, 'favorites in profile';
  perform set_list_status(30, 'on_hold'); perform set_favorite(1512, true);
  perform note_anime(1512, 'One Piece', '', array['Приключения', ' Экшен ', 'Экшен', ''], 1100);
  assert (select cardinality(genres) from anime_meta where anime_id = 1512) = 2 and (select episodes from anime_meta where anime_id = 1512) = 1100, 'note_anime';
end $$;
select pg_temp.expect_error($q$select set_favorite(999999, true)$q$, 'not_in_list');

-- Просмотры для рейтинга, статистики и сводки.
reset role;
insert into watch_awards (user_id, anime_id, episode, video_id, seconds, points, awarded_at)
select p.id, 1512, e::text, 0, 1400, 10, now() - (e || ' hours')::interval from profiles p, generate_series(1, 6) e where p.username = 'ren';
insert into watch_awards (user_id, anime_id, episode, video_id, seconds, points, awarded_at)
select p.id, 21748, e::text, 0, 1400, 10, now() - (e || ' hours')::interval from profiles p, generate_series(1, 3) e where p.username = 'mika';
insert into watch_awards (user_id, anime_id, episode, video_id, seconds, points, awarded_at)
select p.id, 21748, e::text, 0, 1400, 10, now() - (e || ' hours')::interval from profiles p, generate_series(1, 9) e where p.username = 'stranger';
set role authenticated;
do $$ declare r jsonb; s jsonb; y jsonb; begin
  r := leaderboard('friends'); assert jsonb_array_length(r) = 2 and r->0->>'username' = 'ren' and (r->0->>'me')::boolean, 'friends board: ' || r::text;
  r := leaderboard('global'); assert r->0->>'username' = 'stranger' and (r->1->>'place')::int = 2, 'global board: ' || r::text;
  s := watch_stats('ren');
  assert (s->>'episodes')::int = 6 and (s->>'hours')::numeric = 2.3 and (s->>'avg_score')::numeric = 9 and s->'genres'->0->>'genre' = 'Экшен', 'stats: ' || s::text;
  assert jsonb_array_length(s->'weekdays') = 7, 'weekdays';
  y := year_summary();
  assert (y->>'episodes')::int = 6 and y->'top_anime'->0->>'title' = 'Ван-Пис' and jsonb_array_length(y->'months') = 12, 'summary: ' || y::text;
  assert (y->>'top_percent')::int between 1 and 100 and y->'genres'->0->>'genre' is not null, 'summary extras: ' || y::text;
end $$;
select set_config('request.jwt.claim.sub', 'f1000000-0000-0000-0000-000000000001', false);
do $$ declare r jsonb; begin
  r := friend_recommendations();
  assert exists (select 1 from jsonb_array_elements(r) e where (e->>'anime_id')::int = 1512) and not exists (select 1 from jsonb_array_elements(r) e where (e->>'anime_id')::int = 21748), 'recs: ' || r::text;
end $$;
-- Аноним видит метки, комментарии и статистику, но не пишет.
set role anon;
do $$ begin assert jsonb_array_length(episode_comments(10, '1')) = 2 and anime_intro(10, '1') ? 'opening' and watch_stats('ren') is not null, 'anon read'; end $$;
select pg_temp.expect_error($q$select post_comment(10, '1', 'x')$q$, 'permission denied');
reset role;
\echo 'All party checks passed.'

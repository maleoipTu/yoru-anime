-- Проверки 0.11: синхронизация прогресса просмотра, любимая озвучка, просмотренные серии.
\set ON_ERROR_STOP 1
insert into auth.users(id, raw_user_meta_data) values
 ('a1000000-0000-0000-0000-000000000001','{"username":"sora"}'),
 ('a2000000-0000-0000-0000-000000000002','{"username":"kai"}');
create or replace function pg_temp.expect_error(statement text, expected text) returns void language plpgsql as $$
begin
  execute statement;
  raise exception 'expected error "%" for: %', expected, statement;
exception when others then
  if sqlerrm not like '%' || expected || '%' then raise exception 'expected "%" got "%" for: %', expected, sqlerrm, statement; end if;
end $$;
grant execute on function pg_temp.expect_error(text, text) to anon, authenticated;

set role anon;
select pg_temp.expect_error($q$select save_progress(1, '1', '', '', 0, 0)$q$, 'permission denied');
reset role;
set role authenticated;
select set_config('request.jwt.claim.sub', 'a1000000-0000-0000-0000-000000000001', false);
do $$ declare r jsonb; begin
  r := save_progress(21, '3', 'AniLibria', 'Kodik', 615.5, 1420, 'Ван-Пис', '//static.yani.tv/p.webp');
  assert r->>'episode' = '3' and r->>'dubbing' = 'AniLibria' and (r->>'position')::real = 615.5 and r->>'poster' = 'https://static.yani.tv/p.webp', 'saved: ' || r::text;
  r := save_progress(21, '4', '', '', 10, 1420);
  assert r->>'episode' = '4' and r->>'dubbing' = 'AniLibria' and r->>'player' = 'Kodik' and r->>'title' = 'Ван-Пис', 'empty voice keeps favourite: ' || r::text;
  r := set_watched(21, '3', true); r := set_watched(21, '3', true); r := set_watched(21, '1', true);
  assert r->'watched' @> '["1","3"]' and jsonb_array_length(r->'watched') = 2, 'watched without duplicates: ' || r::text;
  r := set_watched(21, '1', false); assert r->'watched' = '["3"]', 'unwatched: ' || r::text;
  r := set_watched(55, '1', true); assert r->>'episode' = '' and r->'watched' = '["1"]', 'watched creates row';
  assert jsonb_array_length(my_progress()) = 2 and (my_progress()->0->>'anime_id')::bigint = 55, 'my progress newest first';
  assert jsonb_array_length(my_progress(now() + interval '1 minute')) = 0, 'since filter';
  assert anime_progress(21)->>'episode' = '4' and anime_progress(999) is null, 'anime progress';
end $$;
select pg_temp.expect_error($q$select save_progress(0, '1', '', '', 0, 0)$q$, 'bad_progress');
select pg_temp.expect_error($q$select set_watched(21, '', true)$q$, 'bad_progress');
select pg_temp.expect_error($q$select save_progress(21, '1', '', '', -5, 0)$q$, 'bad_progress');
select set_config('request.jwt.claim.sub', 'a2000000-0000-0000-0000-000000000002', false);
do $$ begin
  assert anime_progress(21) is null and jsonb_array_length(my_progress()) = 0, 'progress is private';
  assert (select count(*) from watch_progress) = 0, 'rls hides others';
end $$;
reset role;
\echo All progress checks passed.

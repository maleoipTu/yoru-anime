-- Проверки админ-панели, блокировки, кастомизации профиля и хранилища (после всех миграций).
\set ON_ERROR_STOP 1
insert into auth.users(id, raw_user_meta_data, email) values
 ('d0000000-0000-0000-0000-00000000000d','{"username":"boss"}','boss@yoru.test'),
 ('e0000000-0000-0000-0000-00000000000e','{"username":"troll"}','troll@yoru.test');

create or replace function pg_temp.expect_error(statement text, expected text) returns void language plpgsql as $$
begin
  execute statement;
  raise exception 'expected error "%" for: %', expected, statement;
exception when others then
  if sqlerrm not like '%' || expected || '%' then raise exception 'expected "%" got "%" for: %', expected, sqlerrm, statement; end if;
end $$;
grant execute on function pg_temp.expect_error(text, text) to anon, authenticated;

set role authenticated;
select set_config('request.jwt.claim.sub', 'd0000000-0000-0000-0000-00000000000d', false);
-- Без записи в admins доступа нет.
do $$ begin assert not is_admin(), 'not admin by default'; end $$;
select pg_temp.expect_error($q$select admin_stats()$q$, 'admin_only');
select pg_temp.expect_error($q$insert into admins(user_id) values (auth.uid())$q$, 'permission denied');
reset role;
insert into admins (user_id, note) select id, 'test' from profiles where username = 'boss';
set role authenticated;
do $$ declare s jsonb; u jsonb; p profiles; begin
  assert is_admin(), 'admin granted via DB';
  s := admin_stats(); assert (s->>'users')::int >= 2, 'stats: ' || s;
  u := admin_users('troll'); assert jsonb_array_length(u) = 1 and u->0->>'email' = 'troll@yoru.test', 'search by nick: ' || u;
  p := admin_adjust_points('e0000000-0000-0000-0000-00000000000e', 500, 'конкурс'); assert p.points = 500, 'points granted';
  p := admin_adjust_points('e0000000-0000-0000-0000-00000000000e', -9999); assert p.points = 0, 'points floor at 0';
  perform admin_grant_decoration('e0000000-0000-0000-0000-00000000000e', 'frame_gold');
  p := admin_set_banned('e0000000-0000-0000-0000-00000000000e', true, 'спам'); assert p.banned and p.ban_reason = 'спам', 'banned';
  assert jsonb_array_length(admin_log_list()) = 4, 'log: ' || admin_log_list();
end $$;
select pg_temp.expect_error($q$select admin_set_banned(auth.uid(), true)$q$, 'self_action');
-- Заблокированный ничего не может.
select set_config('request.jwt.claim.sub', 'e0000000-0000-0000-0000-00000000000e', false);
select pg_temp.expect_error($q$select award_watch(1, 1, '1', 900)$q$, 'banned');
select pg_temp.expect_error($q$select set_review(1, 5, 'x')$q$, 'banned');
select pg_temp.expect_error($q$select send_friend_request('boss')$q$, 'banned');
select pg_temp.expect_error($q$select update_profile(p_bio => 'x')$q$, 'banned');
select pg_temp.expect_error($q$select admin_stats()$q$, 'admin_only');
-- Кастомизация профиля.
select set_config('request.jwt.claim.sub', 'd0000000-0000-0000-0000-00000000000d', false);
do $$ declare p profiles; r jsonb; begin
  p := update_profile(p_bio => '  Люблю сёнэн  ', p_pronouns => 'он/его', p_banner_url => 'https://x.supabase.co/storage/v1/object/public/media/d/banner.gif',
    p_theme_primary => '#FF5263', p_theme_accent => '#2B1A3A');
  assert p.bio = 'Люблю сёнэн' and p.pronouns = 'он/его' and p.theme_primary = '#FF5263', 'customization saved';
  p := update_profile(p_status => 'Смотрю'); assert p.bio = 'Люблю сёнэн', 'null keeps fields';
  r := public_profile('boss')->'profile'; assert r->>'banner_url' like '%banner.gif' and r->>'theme_accent' = '#2B1A3A', 'public profile has customization';
end $$;
select pg_temp.expect_error($q$select update_profile(p_theme_primary => 'red')$q$, 'profiles_theme_check');
select pg_temp.expect_error($q$select update_profile(p_bio => repeat('a', 191))$q$, 'profiles_bio_check');
select pg_temp.expect_error($q$select update_profile(p_banner_url => 'http://evil')$q$, 'profiles_banner_check');
-- Хранилище: только своя папка.
insert into storage.objects(bucket_id, name) values ('media', 'd0000000-0000-0000-0000-00000000000d/avatar-1.gif');
select pg_temp.expect_error($q$insert into storage.objects(bucket_id, name) values ('media', 'e0000000-0000-0000-0000-00000000000e/avatar.gif')$q$, 'row-level security');
reset role;
do $$ begin assert (select file_size_limit from storage.buckets where id = 'media') = 8388608, 'bucket limit'; end $$;
\echo 'All admin checks passed.'

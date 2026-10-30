-- Проверки правил аккаунтов: запускается Tests/database.sh на чистой базе после миграции.
\set ON_ERROR_STOP 1
insert into auth.users(id, raw_user_meta_data) values
 ('11111111-1111-1111-1111-111111111111','{"username":"Haruka_01"}'),
 ('22222222-2222-2222-2222-222222222222','{"username":"haruka_01"}');

create or replace function pg_temp.expect_error(statement text, expected text) returns void language plpgsql as $$
begin
  execute statement;
  raise exception 'expected error "%" for: %', expected, statement;
exception when others then
  if sqlerrm not like '%' || expected || '%' then raise exception 'expected "%" got "%" for: %', expected, sqlerrm, statement; end if;
end $$;
grant execute on function pg_temp.expect_error(text, text) to anon, authenticated;

do $$ begin
  assert (select username from profiles where id = '11111111-1111-1111-1111-111111111111') = 'haruka_01', 'username normalized';
  assert (select username from profiles where id = '22222222-2222-2222-2222-222222222222') = 'user_22222222', 'taken username falls back';
end $$;

set role authenticated;
select set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', false);
do $$ declare r jsonb; begin
  r := award_watch(10, 100, '1', 120); assert r->>'reason' = 'too_short', 'short watch rejected';
  r := award_watch(10, 100, '1', 600); assert (r->>'points')::int = 15 and (r->>'awarded')::bool, 'first episode of new anime gives 15';
  r := award_watch(10, 101, '1', 1400); assert r->>'reason' = 'already', 'other voice of same episode not rewarded';
  r := award_watch(10, 102, '2', 1400); assert r->>'reason' = 'cooldown', 'cooldown between awards';
end $$;
reset role;
update watch_awards set awarded_at = now() - interval '10 minutes';
set role authenticated;
do $$ declare r jsonb; begin
  r := award_watch(10, 102, '2', 1400); assert (r->>'points')::int = 10 and (r->>'balance')::int = 25, 'next episode gives 10';
end $$;
select pg_temp.expect_error($q$update profiles set points = 99999 where id = auth.uid()$q$, 'permission denied');
select pg_temp.expect_error($q$insert into inventory values (auth.uid(), 'frame_gold')$q$, 'permission denied');
select pg_temp.expect_error($q$select buy_decoration('frame_gold')$q$, 'level_too_low');
select pg_temp.expect_error($q$select buy_decoration('frame_sakura')$q$, 'not_enough_points');
select pg_temp.expect_error($q$select buy_decoration('frame_moon')$q$, 'already_owned');
select pg_temp.expect_error($q$select equip_decoration('frame', 'frame_sakura')$q$, 'not_owned');
select pg_temp.expect_error($q$select change_username('user_22222222')$q$, 'username_taken');
select pg_temp.expect_error($q$select update_profile(p_avatar_url => 'javascript:alert(1)')$q$, 'avatar_url');
select pg_temp.expect_error($q$select update_profile(p_showcase => '[1,2,3,4,5,6,7]')$q$, 'showcase');
reset role;
update profiles set points = 160, total_earned = 160 where id = '11111111-1111-1111-1111-111111111111';
set role authenticated;
do $$ declare p profiles; begin
  p := buy_decoration('frame_sakura'); assert p.points = 10, 'purchase deducts price';
  p := equip_decoration('frame', 'frame_sakura'); assert p.equipped_frame = 'frame_sakura', 'owned frame equips';
  p := equip_decoration('badge', 'badge_moon'); assert p.equipped_badge = 'badge_moon', 'free badge equips';
  p := equip_decoration('badge', ''); assert p.equipped_badge = '', 'badge can be removed';
  p := equip_decoration('frame', ''); assert p.equipped_frame = 'frame_moon', 'frame resets to default';
  p := update_profile(p_display_name => ' Харука ', p_status => 'Смотрю Фрирен'); assert p.display_name = 'Харука' and p.status = 'Смотрю Фрирен', 'profile fields update';
  p := change_username('new_name'); assert p.username = 'new_name', 'username changes';
  assert username_available('haruka_01') and not username_available('new_name') and not username_available('Bad name'), 'username availability';
  assert (select count(*) from profiles where status <> '') = 1, 'cannot touch other profiles';
end $$;
update profiles set status = 'hack' where id = '22222222-2222-2222-2222-222222222222';
reset role;
do $$ begin assert (select status from profiles where id = '22222222-2222-2222-2222-222222222222') = '', 'other profile untouched'; end $$;
select set_config('request.jwt.claim.sub', '', false);
set role anon;
do $$ begin assert (select count(*) from decorations) = 21 and (select count(*) from profiles) = 2, 'public read'; end $$;
select pg_temp.expect_error($q$select award_watch(10, 1, '3', 900)$q$, 'permission denied');
select pg_temp.expect_error($q$select count(*) from watch_awards$q$, 'permission denied');
reset role;
\echo 'All database checks passed.'

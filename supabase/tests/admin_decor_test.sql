-- Проверки набора администрации «Хранитель ночи» (после всех миграций; boss — админ из admin_test.sql).
\set ON_ERROR_STOP 1
insert into auth.users(id, raw_user_meta_data) values
 ('a7000000-0000-0000-0000-000000000007','{"username":"novice"}'),
 ('a8000000-0000-0000-0000-000000000008','{"username":"watcher"}');

create or replace function pg_temp.expect_error(statement text, expected text) returns void language plpgsql as $$
begin
  execute statement;
  raise exception 'expected error "%" for: %', expected, statement;
exception when others then
  if sqlerrm not like '%' || expected || '%' then raise exception 'expected "%" got "%" for: %', expected, sqlerrm, statement; end if;
end $$;
grant execute on function pg_temp.expect_error(text, text) to anon, authenticated;

-- Набор: шесть украшений, все эксклюзивные, бесплатные и только для админов; существующий админ получил всё при миграции.
do $$ begin
  assert (select count(*) from decorations where admin_only) = 6, 'six admin decorations';
  assert not exists (select 1 from decorations where admin_only and (not exclusive or price <> 0 or not active)), 'exclusive, free, active';
  assert (select array_agg(distinct kind order by kind) from decorations where admin_only)
    = array['avatar_deco', 'badge', 'frame', 'name_color', 'profile_effect', 'profile_frame'], 'every kind covered';
  assert (select count(*) from inventory i join decorations d on d.id = i.decoration_id join profiles p on p.id = i.user_id
    where d.admin_only and p.username = 'boss') = 6, 'existing admin got the set';
  assert not exists (select 1 from inventory i join decorations d on d.id = i.decoration_id join profiles p on p.id = i.user_id
    where d.admin_only and p.username <> 'boss'), 'nobody else got it';
end $$;

-- Админ надевает набор; его профиль у других рисуется через worn_decorations().
set role authenticated;
select set_config('request.jwt.claim.sub', 'd0000000-0000-0000-0000-00000000000d', false);
do $$ declare p profiles; begin
  assert (select count(*) from decorations where admin_only) = 6, 'admin sees the set in catalog';
  p := equip_decoration('avatar_deco', 'deco_admin_keeper'); p := equip_decoration('profile_effect', 'fx_admin_watch');
  p := equip_decoration('profile_frame', 'pf_admin_crown'); p := equip_decoration('frame', 'frame_admin');
  p := equip_decoration('badge', 'badge_admin'); p := equip_decoration('name_color', 'color_admin');
  assert p.equipped_deco = 'deco_admin_keeper' and p.equipped_effect = 'fx_admin_watch' and p.equipped_pframe = 'pf_admin_crown'
    and p.equipped_frame = 'frame_admin' and p.equipped_badge = 'badge_admin' and p.equipped_color = 'color_admin', 'admin wears the set';
end $$;
select pg_temp.expect_error($q$select buy_decoration('deco_admin_keeper')$q$, 'already_owned');

-- Обычный пользователь: в каталоге набора нет, надеть, купить, подарить и выдать нельзя; надетое админом видно.
select set_config('request.jwt.claim.sub', 'f1000000-0000-0000-0000-000000000001', false);
do $$ declare w jsonb; r jsonb; begin
  assert not exists (select 1 from decorations where admin_only), 'catalog hides admin set from users';
  assert (select count(*) from decorations) > 20, 'regular catalog still visible';
  r := public_profile('boss')->'profile';
  assert r->>'equipped_deco' = 'deco_admin_keeper' and r->>'equipped_pframe' = 'pf_admin_crown', 'public profile shows admin decor: ' || r::text;
  w := worn_decorations();
  assert jsonb_array_length(w) = 6, 'worn styles available: ' || w::text;
  assert (select bool_and((e->>'admin_only')::boolean) from jsonb_array_elements(w) e), 'worn list is admin-only';
  assert w @> '[{"id":"deco_admin_keeper","style":{"type":"night_keeper"}}]', 'style included';
end $$;
select pg_temp.expect_error($q$select equip_decoration('avatar_deco', 'deco_admin_keeper')$q$, 'admin_only');
select pg_temp.expect_error($q$select equip_decoration('badge', 'badge_admin')$q$, 'admin_only');
select pg_temp.expect_error($q$select buy_decoration('fx_admin_watch')$q$, 'achievement_only');
select pg_temp.expect_error($q$select gift_decoration('f2000000-0000-0000-0000-000000000002', 'pf_admin_crown')$q$, 'not_giftable');
select pg_temp.expect_error($q$select admin_grant_decoration(auth.uid(), 'badge_admin')$q$, 'admin_only');
select pg_temp.expect_error($q$insert into inventory(user_id, decoration_id) values (auth.uid(), 'badge_admin')$q$, 'permission denied');
select pg_temp.expect_error($q$update decorations set admin_only = false where id = 'badge_admin'$q$, 'permission denied');

-- Гость тоже не видит набор в каталоге, но видит надетое.
set role anon;
select set_config('request.jwt.claim.sub', '', false);
do $$ begin
  assert not exists (select 1 from decorations where admin_only), 'anon catalog hides admin set';
  assert jsonb_array_length(worn_decorations()) = 6, 'anon sees worn styles';
end $$;

-- Админ не может выдать набор не-админу даже из админки.
set role authenticated;
select set_config('request.jwt.claim.sub', 'd0000000-0000-0000-0000-00000000000d', false);
select pg_temp.expect_error($q$select admin_grant_decoration('f1000000-0000-0000-0000-000000000001', 'color_admin')$q$, 'admin_only');
do $$ begin perform admin_grant_decoration('f1000000-0000-0000-0000-000000000001', 'deco_cat_ears'); end $$;

-- Новый админ получает набор автоматически; снятый — теряет его и надетое снимается.
reset role;
insert into admins (user_id, note) values ('a7000000-0000-0000-0000-000000000007', 'новичок');
insert into admins (user_id, note) values ('a7000000-0000-0000-0000-000000000007', 'повтор') on conflict do nothing;
do $$ begin
  assert (select count(*) from inventory i join decorations d on d.id = i.decoration_id
    where d.admin_only and i.user_id = 'a7000000-0000-0000-0000-000000000007') = 6, 'new admin got the set';
end $$;
set role authenticated;
select set_config('request.jwt.claim.sub', 'a7000000-0000-0000-0000-000000000007', false);
do $$ declare p profiles; begin
  assert (select count(*) from decorations where admin_only) = 6, 'new admin sees the set';
  p := equip_decoration('profile_frame', 'pf_admin_crown'); p := equip_decoration('name_color', 'color_admin');
  assert p.equipped_pframe = 'pf_admin_crown', 'new admin wears frame';
end $$;
reset role;
delete from admins where user_id = 'a7000000-0000-0000-0000-000000000007';
do $$ declare p profiles; begin
  select * into p from profiles where id = 'a7000000-0000-0000-0000-000000000007';
  assert p.equipped_pframe = '' and p.equipped_color = 'color_default', 'removed admin unequipped: ' || row_to_json(p)::text;
  assert not exists (select 1 from inventory i join decorations d on d.id = i.decoration_id
    where d.admin_only and i.user_id = 'a7000000-0000-0000-0000-000000000007'), 'removed admin lost the set';
end $$;
set role authenticated;
select set_config('request.jwt.claim.sub', 'a7000000-0000-0000-0000-000000000007', false);
select pg_temp.expect_error($q$select equip_decoration('profile_frame', 'pf_admin_crown')$q$, 'admin_only');
do $$ begin assert not exists (select 1 from decorations where admin_only), 'former admin no longer sees the set'; end $$;

-- Новое админское украшение сразу у всех админов; удаление профиля админа ничего не ломает.
reset role;
insert into decorations (id, kind, title, price, rarity, style, sort, exclusive, admin_only)
  values ('badge_admin_test', 'badge', 'Тест', 0, 'legendary', '{"glyph":"✦","color":"#FF8A3D"}', 999, true, true);
do $$ begin
  assert exists (select 1 from inventory where user_id = 'd0000000-0000-0000-0000-00000000000d' and decoration_id = 'badge_admin_test'), 'new admin decor granted';
end $$;
delete from decorations where id = 'badge_admin_test';
insert into admins (user_id) values ('a8000000-0000-0000-0000-000000000008');
delete from auth.users where id = 'a8000000-0000-0000-0000-000000000008';
delete from profiles where id = 'a8000000-0000-0000-0000-000000000008';
do $$ begin assert not exists (select 1 from admins where user_id = 'a8000000-0000-0000-0000-000000000008'), 'cascade ok'; end $$;
\echo 'All admin decor checks passed.'

-- Yoru: эксклюзивный набор украшений администрации — «Хранитель ночи».
-- Применяется после 20261102000000_progress_sync.sql; идемпотентна (можно прогнать повторно).
--
-- Правила:
--  * украшения с admin_only = true нельзя купить, подарить и надеть, если ты не в таблице admins;
--  * в каталоге (REST /decorations) такие строки видят только админы и владельцы, остальным они не показываются;
--  * чтобы надетое админом украшение рисовалось у всех (публичный профиль, друзья, комментарии, рейтинг),
--    клиенты дополнительно получают стили надетых админских украшений через worn_decorations();
--  * набор выдаётся всем админам сразу, новым админам — автоматически (триггер), при снятии прав — забирается.

-- ───────────────────────── Флаг «только для администрации» ─────────────────────────
alter table public.decorations add column if not exists admin_only boolean not null default false;

-- Видит ли текущий пользователь (или гость) админское украшение: админ или владелец.
-- security definer: таблица admins закрыта для anon/authenticated, а is_admin() гостям не выдана.
create or replace function public.can_see_decoration(p_id text)
returns boolean language sql stable security definer set search_path = public as $$
  select auth.uid() is not null and (
    exists (select 1 from admins where user_id = auth.uid())
    or exists (select 1 from inventory where user_id = auth.uid() and decoration_id = p_id));
$$;
revoke execute on function public.can_see_decoration(text) from public;
grant execute on function public.can_see_decoration(text) to anon, authenticated;

drop policy if exists "decorations are public" on public.decorations;
create policy "decorations are public" on public.decorations for select
  using (active and (not admin_only or public.can_see_decoration(id)));

-- Стили админских украшений, которые сейчас кто-то носит: без них профиль админа у других выглядел бы пустым.
-- Отдаются только надетые (их и так видно на публичном профиле), весь админский набор не раскрывается.
create or replace function public.worn_decorations()
returns jsonb language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object('id', d.id, 'kind', d.kind, 'title', d.title, 'description', d.description,
      'price', d.price, 'min_level', d.min_level, 'rarity', d.rarity, 'style', d.style, 'sort', d.sort, 'active', d.active,
      'exclusive', d.exclusive, 'admin_only', d.admin_only, 'available_from', d.available_from, 'available_until', d.available_until)
      order by d.kind, d.sort), '[]'::jsonb)
  from decorations d
  where d.active and d.admin_only and exists (select 1 from profiles p
    where d.id in (p.equipped_frame, p.equipped_badge, p.equipped_color, p.equipped_deco, p.equipped_effect, p.equipped_pframe));
$$;
revoke execute on function public.worn_decorations() from public;
grant execute on function public.worn_decorations() to anon, authenticated;

-- ───────────────────────── Надевание: логика из 20261101000000_party_stats.sql + проверка админа ─────────────────────────
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
    if d.admin_only and not exists (select 1 from admins where user_id = me) then
      raise exception 'admin_only' using errcode = '42501';
    end if;
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

-- Выдача из админки: админский набор — только админам (логика из 20261031000000_admin_customization.sql + проверка).
create or replace function public.admin_grant_decoration(p_user uuid, p_decoration text)
returns void language plpgsql security definer set search_path = public as $$
begin
  perform require_admin();
  if not exists (select 1 from decorations where id = p_decoration) then raise exception 'unknown_decoration' using errcode = 'P0002'; end if;
  if exists (select 1 from decorations where id = p_decoration and admin_only) and not exists (select 1 from admins where user_id = p_user) then
    raise exception 'admin_only' using errcode = '42501';
  end if;
  insert into inventory (user_id, decoration_id) values (p_user, p_decoration) on conflict do nothing;
  perform admin_write_log('grant_decoration', p_user, jsonb_build_object('decoration', p_decoration));
end $$;

-- ───────────────────────── Выдача и отзыв набора ─────────────────────────
-- Заблокированным не выдаём (на inventory стоит триггер block_banned) — им выдадут после разблокировки следующим прогоном
-- или вручную через admin_grant_decoration.
create or replace function public.grant_admin_decor(p_user uuid)
returns void language sql security definer set search_path = public as $$
  insert into inventory (user_id, decoration_id)
  select p_user, d.id from decorations d
  where d.admin_only and exists (select 1 from profiles p where p.id = p_user and not p.banned)
  on conflict do nothing;
$$;

create or replace function public.on_admin_added()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  perform grant_admin_decor(new.user_id);
  return new;
end $$;

-- Права сняли — админский набор забираем и снимаем с профиля (если профиль ещё существует).
create or replace function public.on_admin_removed()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if exists (select 1 from admins where user_id = old.user_id) then return old; end if;
  delete from inventory i using decorations d where i.user_id = old.user_id and i.decoration_id = d.id and d.admin_only;
  update profiles p set
    equipped_frame  = case when p.equipped_frame  in (select id from decorations where admin_only) then 'frame_moon' else p.equipped_frame end,
    equipped_badge  = case when p.equipped_badge  in (select id from decorations where admin_only) then '' else p.equipped_badge end,
    equipped_color  = case when p.equipped_color  in (select id from decorations where admin_only) then 'color_default' else p.equipped_color end,
    equipped_deco   = case when p.equipped_deco   in (select id from decorations where admin_only) then '' else p.equipped_deco end,
    equipped_effect = case when p.equipped_effect in (select id from decorations where admin_only) then '' else p.equipped_effect end,
    equipped_pframe = case when p.equipped_pframe in (select id from decorations where admin_only) then '' else p.equipped_pframe end
  where p.id = old.user_id;
  return old;
end $$;

-- Новое админское украшение (например, добавленное позже миграцией) сразу появляется у всех админов.
create or replace function public.on_admin_decor_added()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if new.admin_only then
    insert into inventory (user_id, decoration_id)
    select a.user_id, new.id from admins a join profiles p on p.id = a.user_id and not p.banned
    on conflict do nothing;
  end if;
  return new;
end $$;

revoke execute on function public.grant_admin_decor(uuid), public.on_admin_added(), public.on_admin_removed(), public.on_admin_decor_added()
  from public, anon, authenticated;

drop trigger if exists admin_decor_grant on public.admins;
create trigger admin_decor_grant after insert on public.admins for each row execute function public.on_admin_added();
drop trigger if exists admin_decor_revoke on public.admins;
create trigger admin_decor_revoke after delete on public.admins for each row execute function public.on_admin_removed();
drop trigger if exists admin_decor_new on public.decorations;
create trigger admin_decor_new after insert or update of admin_only on public.decorations for each row execute function public.on_admin_decor_added();

-- ───────────────────────── Набор «Хранитель ночи» ─────────────────────────
-- Рисунки оригинальные: style.type рисует клиент (спрайты night_keeper, fx_night*, pf_night_crest).
-- Стиль строгий — знак статуса, а не праздник: тонкие линии, лунное серебро #DCE3F0 и графит, зарёй (#FF8A3D → #FF4D67)
-- окрашена лишь одна маленькая деталь, движение медленное и едва заметное.
insert into public.decorations (id, kind, title, description, price, min_level, rarity, style, sort, exclusive, admin_only, available_from, available_until) values
  ('deco_admin_keeper', 'avatar_deco',    'Хранитель ночи',   'Тонкий лунный ореол с серебряным полумесяцем и ромбом зари. Только для администрации', 0, 1, 'legendary',
    '{"type":"night_keeper","colors":["#DCE3F0","#9AA4B6","#FF8A3D"],"animated":true}', 300, true, true, null, null),
  ('fx_admin_watch',    'profile_effect', 'Ночной дозор',     'Редкие тусклые звёзды, медленно мерцают; изредка — тонкая комета. Только для администрации', 0, 1, 'legendary',
    '{"type":"night_watch","colors":["#DCE3F0","#9AA4B6"]}', 300, true, true, null, null),
  ('pf_admin_crown',    'profile_frame',  'Полуночная грань', 'Тонкий серебряный контур, угловые скобки и лунная эмблема. Только для администрации', 0, 1, 'legendary',
    '{"type":"night_crown","colors":["#DCE3F0","#9AA4B6","#FF8A3D"]}', 300, true, true, null, null),
  ('frame_admin',       'frame',          'Кольцо дозора',    'Тонкое серебряное кольцо с едва заметным отсветом зари. Только для администрации', 0, 1, 'legendary',
    '{"colors":["#DCE3F0","#8E98AB","#DCE3F0","#8E98AB","#DCE3F0","#D4A994"],"thin":true}', 300, true, true, null, null),
  ('badge_admin',       'badge',          'Хранитель',        'Знак администрации Yoru', 0, 1, 'legendary',
    '{"glyph":"☾","color":"#DCE3F0"}', 300, true, true, null, null),
  ('color_admin',       'name_color',     'Лунное серебро',   'Цвет ника администрации', 0, 1, 'legendary',
    '{"colors":["#EEF2F8","#B9C3D4"]}', 300, true, true, null, null)
on conflict (id) do update set
  kind = excluded.kind, title = excluded.title, description = excluded.description, price = excluded.price, min_level = excluded.min_level,
  rarity = excluded.rarity, style = excluded.style, sort = excluded.sort, exclusive = excluded.exclusive, admin_only = excluded.admin_only,
  available_from = excluded.available_from, available_until = excluded.available_until;

-- Всем текущим админам — весь набор.
do $$ declare a record; begin
  for a in select user_id from public.admins loop perform public.grant_admin_decor(a.user_id); end loop;
end $$;

-- Рисованные значки: у встроенных значков появляется style.sprite (лист bd_* из art/assets/decor).
-- Символ (glyph) остаётся — его показывают старые версии приложений и значки, созданные в админ-панели.
update public.decorations
   set style = style || jsonb_build_object('sprite', 'bd_' || substring(id from 7))
 where kind = 'badge'
   and id in ('badge_moon','badge_star','badge_sakura','badge_spark','badge_blade','badge_balance','badge_crown',
              'badge_finisher','badge_critic','badge_heart','badge_pumpkin','badge_snow','badge_admin');

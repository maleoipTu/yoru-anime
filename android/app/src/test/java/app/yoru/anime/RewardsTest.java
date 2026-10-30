package app.yoru.anime;
import org.junit.Test;
import static org.junit.Assert.*;

public class RewardsTest {
    @Test public void levelsMatchDatabaseFormula(){assertEquals(1,Rewards.level(0));assertEquals(1,Rewards.level(49));assertEquals(2,Rewards.level(50));assertEquals(3,Rewards.level(200));assertEquals(5,Rewards.level(800));assertEquals(1,Rewards.level(-5));}
    @Test public void progressToNextLevel(){assertEquals(50,Rewards.toNext(0));assertEquals(150,Rewards.toNext(50));assertEquals(0,Rewards.progress(0));assertEquals(500,Rewards.progress(125));assertTrue(Rewards.progress(199)<1000);}
    @Test public void watchThresholdIsBetweenFiveAndTwelveMinutes(){assertEquals(600_000,Rewards.threshold(-1));assertEquals(300_000,Rewards.threshold(240_000));assertEquals(720_000,Rewards.threshold(1_440_000));assertEquals(540_000,Rewards.threshold(900_000));}
    @Test public void meterCountsOnlyRealPlayback(){
        Rewards.WatchMeter m=new Rewards.WatchMeter();m.start(7);
        m.tick(true,1000);m.tick(true,1500);m.tick(false,2000);m.tick(true,600_000);m.tick(true,600_500);
        assertEquals(1000,m.watched);
        m.tick(true,700_000);assertEquals(3000,m.watched);
        m.start(7);assertEquals(3000,m.watched);m.start(8);assertEquals(0,m.watched);
        for(long t=0;t<=300_000;t+=500)m.tick(true,10_000+t);assertTrue(m.due(240_000));m.sent=true;assertFalse(m.due(240_000));
    }
    @Test public void validatesAccountInput(){assertTrue(Rewards.validUsername("haruka_01"));assertFalse(Rewards.validUsername("Ha"));assertFalse(Rewards.validUsername("имя"));assertTrue(Rewards.validEmail("a@b.ru"));assertFalse(Rewards.validEmail("a@b"));}
    @Test public void parsesDecorationColors(){assertEquals(0xFFBFAEFF,Rewards.color("#BFAEFF",0));assertEquals(0x80FFFFFF,Rewards.color("#80FFFFFF",0));assertEquals(5,Rewards.color("red",5));assertEquals(5,Rewards.color("#XYZXYZ",5));}
    @Test public void explainsServerErrors(){assertEquals("Не хватает очков.",Rewards.error("not_enough_points P0001"));assertEquals("Неверная почта или пароль.",Rewards.error("invalid_credentials Invalid login credentials"));assertTrue(Rewards.skipReason("cooldown").contains("4 минуты"));assertEquals("",Rewards.skipReason("ok"));}
    @Test public void seasonalWindow(){long now=java.time.OffsetDateTime.parse("2026-10-30T12:00:00+00:00").toInstant().toEpochMilli();assertTrue(Rewards.onSale("2026-10-24T00:00:00+00:00","2026-11-04T00:00:00+00:00",now));assertFalse(Rewards.onSale("2026-12-20T00:00:00+00:00","2027-01-15T00:00:00+00:00",now));assertTrue(Rewards.onSale("","",now));assertTrue(Rewards.onSale(null,null,now));}
    @Test public void russianDays(){assertEquals("1 день",Rewards.days(1));assertEquals("3 дня",Rewards.days(3));assertEquals("5 дней",Rewards.days(5));assertEquals("11 дней",Rewards.days(11));assertEquals("22 дня",Rewards.days(22));}
}

package com.cubeclient.mod.features;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CpsDisplayTest {

    @Test
    void noClicksIsZeroCps() {
        long[] now = {0L};
        CpsDisplay display = new CpsDisplay(() -> now[0]);

        assertEquals(0, display.currentCps());
    }

    @Test
    void threeClicksWithinOneSecondCountAsThree() {
        long[] now = {0L};
        CpsDisplay display = new CpsDisplay(() -> now[0]);

        display.recordClick();
        now[0] = 300;
        display.recordClick();
        now[0] = 600;
        display.recordClick();
        now[0] = 900;

        assertEquals(3, display.currentCps());
    }

    // 1초 롤링 윈도우 — 1초보다 오래된 클릭은 더 이상 세지 않는다.
    @Test
    void clicksOlderThanOneSecondAgeOutOfTheWindow() {
        long[] now = {0L};
        CpsDisplay display = new CpsDisplay(() -> now[0]);

        display.recordClick();
        display.recordClick();
        now[0] = 1500;
        display.recordClick();

        assertEquals(1, display.currentCps());
    }

    @Test
    void clickAtExactlyOneSecondAgoIsExcluded() {
        long[] now = {0L};
        CpsDisplay display = new CpsDisplay(() -> now[0]);

        display.recordClick();
        now[0] = 1000;

        assertEquals(0, display.currentCps());
    }

    // recordClick()은 CPS HUD가 꺼져 있어도(=currentCps()/render()가 전혀 호출되지
    // 않아도) 매 틱 호출된다. 정리(eviction)가 currentCps() 안에서만 일어난다면
    // clickTimestamps는 세션 내내 무한정 쌓인다. 이를 검증하려면 큐 크기를 직접 봐야
    // 하는데, 최종 currentCps() 호출 한 번만으로는 예전 버그가 있던 코드에서도 같은
    // 결과가 나온다 (currentCps()의 정리 루프가 그 시점까지 쌓인 오래된 항목을 전부
    // 걸러내기 때문). 그래서 프로덕션 코드에 테스트 전용 접근자를 추가하는 대신,
    // 리플렉션으로 private 필드를 직접 들여다봐서 "쌓이지 않는다"는 것 자체를 증명한다.
    @Test
    void recordClickPrunesExpiredEntriesEvenWhenCurrentCpsIsNeverCalled() throws Exception {
        long[] now = {0L};
        CpsDisplay display = new CpsDisplay(() -> now[0]);

        // currentCps()를 한 번도 호출하지 않은 채, 창(1초)보다 훨씬 넓은 간격(2초)으로
        // 1000번 클릭을 기록한다. recordClick()이 스스로 정리하지 않으면 큐에는 1000개가
        // 그대로 남는다.
        for (int i = 0; i < 1000; i++) {
            display.recordClick();
            now[0] += 2000;
        }

        Field field = CpsDisplay.class.getDeclaredField("clickTimestamps");
        field.setAccessible(true);
        Deque<?> clickTimestamps = (Deque<?>) field.get(display);

        assertEquals(1, clickTimestamps.size(),
            "recordClick() should prune entries older than the window on its own, "
                + "not only when currentCps() is called");

        // 이어서 최근 창 안에 들어오는 클릭 3개를 추가로 기록하고, currentCps()가
        // 여전히 올바른 값을 돌려주는지 확인한다.
        display.recordClick();
        now[0] += 300;
        display.recordClick();
        now[0] += 300;
        display.recordClick();
        now[0] += 300;

        assertEquals(3, display.currentCps());
    }
}

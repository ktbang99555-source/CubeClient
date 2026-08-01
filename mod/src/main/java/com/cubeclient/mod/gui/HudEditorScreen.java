package com.cubeclient.mod.gui;

import com.cubeclient.mod.config.CachedConfig;
import com.cubeclient.mod.config.ModConfig;
import com.cubeclient.mod.registry.Feature;
import com.cubeclient.mod.registry.FeatureRegistry;
import com.cubeclient.mod.registry.PositionedHudFeature;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 켜진 PositionedHudFeature만 보여주고, 각각을 드래그로 옮기고 우하단 핸들로 크기를
 * 조절하는 화면. Screen을 상속하므로 싱글플레이는 일시정지 메뉴와 같은 방식으로 자연히
 * 멈춘다 — 이 화면이 새로 만드는 예외가 아니다.
 */
public class HudEditorScreen extends Screen {
    private static final int HANDLE_SIZE = 8;
    private static final int OVERLAY_MARGIN = 4;
    private static final int EXIT_BUTTON_WIDTH = 100;
    private static final int EXIT_BUTTON_HEIGHT = 20;
    private static final int RESET_BUTTON_WIDTH = 100;

    private final Screen parent;
    private final FeatureRegistry registry;
    private final CachedConfig cachedConfig;

    private final List<Entry> entries = new ArrayList<>();
    private Entry dragging;
    private boolean draggingHandle;
    private double dragStartMouseX;
    private double dragStartMouseY;
    private double dragStartXRatio;
    private double dragStartYRatio;
    private double dragStartScale;

    public HudEditorScreen(Screen parent, FeatureRegistry registry, CachedConfig cachedConfig) {
        super(Text.literal("HUD 조절"));
        this.parent = parent;
        this.registry = registry;
        this.cachedConfig = cachedConfig;
    }

    @Override
    protected void init() {
        // Screen.resize() → refreshWidgetPositions() → clearAndInit() re-runs init() on every
        // window resize, GUI-scale change, and F11 toggle. Without clearing first, entries would
        // accumulate duplicates across resizes, corrupting both saveAll() and hit-testing.
        entries.clear();
        dragging = null;

        ModConfig config = cachedConfig.current();
        for (Feature feature : registry.all()) {
            if (feature instanceof PositionedHudFeature hudFeature && config.isEnabled(hudFeature.id())) {
                HudPosition position = config.positionOr(hudFeature.id(), hudFeature.defaultPosition());
                entries.add(new Entry(hudFeature, position));
            }
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("나가기"), b -> close())
            .dimensions(width / 2 - EXIT_BUTTON_WIDTH / 2, height - EXIT_BUTTON_HEIGHT - 8,
                EXIT_BUTTON_WIDTH, EXIT_BUTTON_HEIGHT)
            .build());

        addDrawableChild(ButtonWidget.builder(Text.literal("위치 초기화"), b -> resetAll())
            .dimensions(width / 2 - EXIT_BUTTON_WIDTH / 2 - RESET_BUTTON_WIDTH - 8, height - EXIT_BUTTON_HEIGHT - 8,
                RESET_BUTTON_WIDTH, EXIT_BUTTON_HEIGHT)
            .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // super.render()를 먼저 호출한다. Screen.render()가 맨 처음 하는 일이
        // renderBackground()이고, 이게 화면 전체에 블러 + 어둡게 하는 텍스처를 씌운다 —
        // entries를 그 다음에 그려야 실제 라이브 HUD 배치를 흐림/어둡게 하지 않은 채로
        // 보여줄 수 있다. 순서를 반대로 하면 편집 화면이 흐릿하고 어두운 스냅샷을 편집하는
        // 꼴이 되어 이 화면의 존재 목적과 정반대가 된다.
        super.render(context, mouseX, mouseY, delta);
        for (Entry entry : entries) {
            entry.feature.render(context, entry.position);
            drawOverlay(context, entry);
        }
    }

    private void drawOverlay(DrawContext context, Entry entry) {
        Bounds bounds = boundsOf(entry);
        context.drawBorder(bounds.x, bounds.y, bounds.width, bounds.height, Theme.ACCENT);
        context.fill(bounds.x + bounds.width - HANDLE_SIZE, bounds.y + bounds.height - HANDLE_SIZE,
            bounds.x + bounds.width, bounds.y + bounds.height, Theme.ACCENT);
    }

    /**
     * 오버레이 사각형의 화면 좌표. 각 기능이 스스로 밝히는 renderedWidth()/renderedHeight()
     * (기본값 80x12, PositionedHudFeature 참고)에 배율을 곱해 근사한다 — 미니맵처럼 크기가
     * 크게 다른 요소는 그 기능이 직접 재정의해서 정확한 히트박스를 제공한다.
     */
    private Bounds boundsOf(Entry entry) {
        int x = (int) (entry.position.xRatio() * width) - OVERLAY_MARGIN;
        int y = (int) (entry.position.yRatio() * height) - OVERLAY_MARGIN;
        int w = (int) (entry.feature.renderedWidth() * entry.position.scale()) + OVERLAY_MARGIN * 2;
        int h = (int) (entry.feature.renderedHeight() * entry.position.scale()) + OVERLAY_MARGIN * 2;
        return new Bounds(x, y, w, h);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        // entries는 render()에서 리스트 순서대로 그려지므로(뒤쪽 = 위에 겹쳐 그려짐), 클릭
        // 판정도 역순으로 훑어야 화면에 보이는 맨 위 요소가 히트테스트를 이긴다. 기본 간격
        // (yRatio 0.05)에서는 오버레이 박스끼리 겹치는 경우가 흔해서, 순서를 맞추지 않으면
        // 시각적으로 위에 있는 요소가 클릭되지 않고 그 아래 가려진 요소가 대신 반응한다.
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry entry = entries.get(i);
            Bounds bounds = boundsOf(entry);
            boolean hitHandle = mouseX >= bounds.x + bounds.width - HANDLE_SIZE && mouseX <= bounds.x + bounds.width
                && mouseY >= bounds.y + bounds.height - HANDLE_SIZE && mouseY <= bounds.y + bounds.height;
            boolean hitBody = mouseX >= bounds.x && mouseX <= bounds.x + bounds.width
                && mouseY >= bounds.y && mouseY <= bounds.y + bounds.height;
            if (hitHandle || hitBody) {
                dragging = entry;
                draggingHandle = hitHandle;
                dragStartMouseX = mouseX;
                dragStartMouseY = mouseY;
                dragStartXRatio = entry.position.xRatio();
                dragStartYRatio = entry.position.yRatio();
                dragStartScale = entry.position.scale();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging == null) {
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        if (draggingHandle) {
            double handleDelta = (mouseX - dragStartMouseX) / (double) dragging.feature.renderedWidth();
            dragging.position = HudPosition.of(
                dragging.position.xRatio(), dragging.position.yRatio(), dragStartScale + handleDelta);
        } else {
            double newXRatio = dragStartXRatio + (mouseX - dragStartMouseX) / width;
            double newYRatio = dragStartYRatio + (mouseY - dragStartMouseY) / height;
            dragging.position = HudPosition.of(newXRatio, newYRatio, dragging.position.scale());
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging != null) {
            saveAll();
            dragging = null;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void resetAll() {
        for (Entry entry : entries) {
            entry.position = entry.feature.defaultPosition();
        }
        saveAll();
    }

    private void saveAll() {
        ModConfig current = cachedConfig.current();
        java.util.Map<String, HudPosition> positions = new java.util.LinkedHashMap<>(current.positions());
        for (Entry entry : entries) {
            positions.put(entry.feature.id(), entry.position);
        }
        try {
            cachedConfig.save(new ModConfig(current.enabled(), current.favorites(), positions));
        } catch (IOException e) {
            if (client != null && client.player != null) {
                client.player.sendMessage(
                    Text.literal("HUD 위치를 저장하지 못했습니다: " + e.getMessage()), false);
            }
        }
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    private static final class Entry {
        final PositionedHudFeature feature;
        HudPosition position;

        Entry(PositionedHudFeature feature, HudPosition position) {
            this.feature = feature;
            this.position = position;
        }
    }

    private record Bounds(int x, int y, int width, int height) {}
}

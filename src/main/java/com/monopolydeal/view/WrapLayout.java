package com.monopolydeal.view;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 自动换行布局 — FlowLayout 的增强版
 *
 * 子组件在容器宽度不足时自动换到下一行。
 * 用于手牌区域，使卡牌在窗口缩小时自动折行显示，无需横向滚动。
 */
public class WrapLayout extends FlowLayout {

    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    /**
     * 获取可用宽度（父容器宽度减去 insets）
     */
    private int getMaxWidth(Container target) {
        Container parent = target.getParent();
        if (parent == null) return Integer.MAX_VALUE;
        int w = parent.getWidth();
        if (w <= 0) return Integer.MAX_VALUE;
        Insets insets = target.getInsets();
        return w - insets.left - insets.right;
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        synchronized (target.getTreeLock()) {
            int maxWidth = getMaxWidth(target);
            int hgap = getHgap();
            int vgap = getVgap();
            int x = 0, y = 0, rowHeight = 0;
            int nmembers = target.getComponentCount();

            for (int i = 0; i < nmembers; i++) {
                Component c = target.getComponent(i);
                if (!c.isVisible()) continue;
                Dimension d = c.getPreferredSize();
                if (x + d.width > maxWidth && x > 0) {
                    y += rowHeight + vgap;
                    x = 0;
                    rowHeight = 0;
                }
                rowHeight = Math.max(rowHeight, d.height);
                x += d.width + hgap;
            }
            return new Dimension(maxWidth, y + rowHeight);
        }
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension dim = preferredLayoutSize(target);
        dim.width = getHgap() * 4;
        return dim;
    }

    @Override
    public void layoutContainer(Container target) {
        synchronized (target.getTreeLock()) {
            int maxWidth = getMaxWidth(target);
            int hgap = getHgap();
            int vgap = getVgap();
            int x = hgap, y = vgap, rowHeight = 0;
            int rowStart = 0;
            List<RowInfo> rows = new ArrayList<>();

            int nmembers = target.getComponentCount();
            for (int i = 0; i < nmembers; i++) {
                Component c = target.getComponent(i);
                if (!c.isVisible()) continue;
                Dimension d = c.getPreferredSize();
                if (x + d.width > maxWidth && i > rowStart) {
                    rows.add(new RowInfo(rowStart, i, y, rowHeight));
                    y += rowHeight + vgap;
                    x = hgap;
                    rowHeight = 0;
                    rowStart = i;
                }
                rowHeight = Math.max(rowHeight, d.height);
                x += d.width + hgap;
            }
            rows.add(new RowInfo(rowStart, nmembers, y, rowHeight));

            for (RowInfo row : rows) {
                x = hgap;
                for (int i = row.startIdx; i < row.endIdx; i++) {
                    Component c = target.getComponent(i);
                    if (!c.isVisible()) continue;
                    Dimension d = c.getPreferredSize();
                    c.setBounds(x, row.y + (row.height - d.height) / 2, d.width, d.height);
                    x += d.width + hgap;
                }
            }
        }
    }

    /** 内部类：记录一行的布局信息 */
    private static class RowInfo {
        final int startIdx, endIdx, y, height;
        RowInfo(int s, int e, int y, int h) {
            this.startIdx = s; this.endIdx = e; this.y = y; this.height = h;
        }
    }
}

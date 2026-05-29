package com.monopolydeal.view;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrap layout — an enhanced version of FlowLayout.
 *
 * Child components automatically wrap to the next row when the container width is insufficient.
 * Used for the hand card area so cards wrap naturally when the window is resized, avoiding
 * horizontal scrolling.
 */
public class WrapLayout extends FlowLayout {

    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    /** Get available width (parent container width minus insets) */
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

    /** Inner class: stores layout info for a single row */
    private static class RowInfo {
        final int startIdx, endIdx, y, height;
        RowInfo(int s, int e, int y, int h) {
            this.startIdx = s; this.endIdx = e; this.y = y; this.height = h;
        }
    }
}

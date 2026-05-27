package com.monopolydeal.view;

import java.awt.*;

/**
 * 自动换行布局管理器 - FlowLayout的增强版
 *
 * 当组件总宽度超过容器可用宽度时，自动将组件折行到下一行显示。
 * 适用于手牌区域的卡牌展示，当手牌较多时可以自动换行而不出现水平滚动条。
 *
 * 用法与FlowLayout一致，传入对齐方式、水平间距和垂直间距即可。
 */
public class WrapLayout extends FlowLayout {

    /**
     * 构造函数
     * @param align 对齐方式（FlowLayout.LEFT/CENTER/RIGHT）
     * @param hgap 水平间距（像素）
     * @param vgap 垂直间距（像素）
     */
    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    /** 尺寸类型常量：首选尺寸 */
    private static final int PREFERRED = 0;
    /** 尺寸类型常量：最小尺寸 */
    private static final int MINIMUM = 1;

    /**
     * 计算布局的首选尺寸
     * 遍历所有可见组件，累加宽度，超过容器宽度时折行
     *
     * @param target 目标容器
     * @param type 尺寸类型（PREFERRED 或 MINIMUM）
     * @return 计算后的首选尺寸
     */
    private Dimension computeSize(Container target, int type) {
        int targetWidth = target.getWidth();
        Insets insets = target.getInsets();
        int maxWidth = targetWidth - insets.left - insets.right;

        if (maxWidth <= 0) {
            // 容器尚未获得宽度，使用极大值以允许单行布局
            maxWidth = Integer.MAX_VALUE;
        }

        int hgap = getHgap();
        int vgap = getVgap();

        int rowWidth = 0;
        int rowHeight = 0;
        int totalWidth = 0;
        int totalHeight = 0;
        int componentCount = 0;

        for (Component comp : target.getComponents()) {
            if (!comp.isVisible()) continue;

            Dimension d = (type == PREFERRED)
                    ? comp.getPreferredSize()
                    : comp.getMinimumSize();

            int compWidth = d.width;
            int compHeight = d.height;

            if (componentCount > 0 && rowWidth + hgap + compWidth > maxWidth) {
                // 换行：保存当前行的宽度和高度
                totalWidth = Math.max(totalWidth, rowWidth);
                totalHeight += rowHeight + vgap;
                rowWidth = 0;
                rowHeight = 0;
            }

            if (rowWidth > 0) {
                rowWidth += hgap;
            }
            rowWidth += compWidth;
            rowHeight = Math.max(rowHeight, compHeight);
            componentCount++;
        }

        // 加上最后一行
        totalWidth = Math.max(totalWidth, rowWidth);
        totalHeight += rowHeight;

        totalWidth += insets.left + insets.right;
        totalHeight += insets.top + insets.bottom;

        return new Dimension(totalWidth, totalHeight);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        synchronized (target.getTreeLock()) {
            return computeSize(target, PREFERRED);
        }
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        synchronized (target.getTreeLock()) {
            return computeSize(target, MINIMUM);
        }
    }
}

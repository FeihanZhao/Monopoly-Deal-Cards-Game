package com.monopolydeal;

import com.monopolydeal.model.*;
import com.monopolydeal.LocalGameFrame;
import javax.swing.*;

public class LocalGameTest {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LocalGameFrame frame = new LocalGameFrame();
            frame.setVisible(true);
        });
    }
}
package com.monopolydeal.pattern.command;

import com.monopolydeal.model.*;

public interface Command {
    void execute();
    void undo();
    String getDescription();
}


package com.aresstack.pyloros.refactoring;

import com.aresstack.pyloros.refactoring.target.MoveProbe;

final class MoveProbeUser {
    String read() {
        return new MoveProbe().value();
    }
}

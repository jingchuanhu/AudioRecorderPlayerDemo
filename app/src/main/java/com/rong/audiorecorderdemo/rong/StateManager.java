package com.rong.audiorecorderdemo.rong;

import java.util.LinkedList;

class StateManager {
    private LinkedList<IRouteState> mStateList = new LinkedList();

    public void add(IRouteState state) {
        mStateList.add(state);
    }

    public void removeAndOffer(IRouteState state) {
        if (mStateList.contains(state)) {
            mStateList.remove(state);
        }
        mStateList.offer(state);
    }

    public int size() {
        return mStateList.size();
    }

    public IRouteState get(int index) {
        return mStateList.get(index);
    }

    public IRouteState peekLast() {
        return mStateList.peekLast();
    }

    public void remove(IRouteState state) {
        if (mStateList.contains(state)) {
            mStateList.remove(state);
        }
    }
}

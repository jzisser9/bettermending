package com.github.hornta.bettermending;

public class MendResult {
  private boolean continueMending;
  private int newExperience;

  MendResult(boolean isCancelled, int newExperience) {
    this.continueMending = isCancelled;
    this.newExperience = newExperience;
  }

  public boolean isContinueMending() {
    return continueMending;
  }

  public int getNewExperience() {
    return newExperience;
  }
}

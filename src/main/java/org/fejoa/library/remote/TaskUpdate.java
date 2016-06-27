/*
 * Copyright 2015.
 * Distributed under the terms of the GPLv3 License.
 *
 * Authors:
 *      Clemens Zeidler <czei002@aucklanduni.ac.nz>
 */
package org.fejoa.library.remote;


public class TaskUpdate {
    final private String taskName;
    final private int totalWork;
    final private int progress;
    final private String progressMessage;

    public TaskUpdate(String taskName, int totalWork, int progress, String progressMessage) {
        this.taskName = taskName;
        this.totalWork = totalWork;
        this.progress = progress;
        this.progressMessage = progressMessage;
    }

    public String getTaskName() {
        return taskName;
    }

    public int getTotalWork() {
        return totalWork;
    }

    public int getProgress() {
        return progress;
    }

    public String getProgressMessage() {
        return progressMessage;
    }

    @Override
    public String toString() {
        String out = taskName;
        if (totalWork > 0 && progress > 0)
            out += " (" + progress + "/" + totalWork + ")";
        out += ": " + progressMessage;
        return out;
    }
}

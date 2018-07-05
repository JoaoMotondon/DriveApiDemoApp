package com.motondon.driveapidemoapp3.model

import android.util.Log
import java.io.Serializable

class TaskModel : Serializable {

    var name: String = ""
    var description: String = ""

    constructor()

    constructor(taskLine: String) {
        val taskParams = taskLine.split("|")

        this.name = taskParams[0]
        this.description = taskParams[1]
    }

    constructor(copy: TaskModel) {
        this.name = copy.name
        this.description = copy.description
    }


    /**
     * We need to override contains, so that we can inform what it really differs one task from another.
     */
    operator fun contains(task: TaskModel): Boolean {
        return task.name == name
    }

    override fun equals(otherItem: Any?): Boolean {
        val other = otherItem as TaskModel
        val ret = name == other.name && description == other.description
        // Log.d("TaskModel", "equals() - task: $name - ret: $ret")
        return ret
    }

    override fun toString(): String {
        return "name: $name - description: $description"
    }
}

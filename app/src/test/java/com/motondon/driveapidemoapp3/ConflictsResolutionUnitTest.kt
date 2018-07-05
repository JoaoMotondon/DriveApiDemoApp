package com.motondon.driveapidemoapp3

import com.motondon.driveapidemoapp3.conflict.TaskConflictUtil
import com.motondon.driveapidemoapp3.model.TaskModel
import org.junit.Test
import junit.framework.Assert.assertEquals
import java.util.*

class ConflictsResolutionUnitTest {

    /**
     * This simulates
     *  - task2 was removed on the server
     *  - task4 was created on the server
     *  - task3 was changed on the server
     */
    @Test
    @Throws(Exception::class)
    fun testServerChanges() {

         var localBaseTaskList: ArrayList<TaskModel> = arrayListOf(TaskModel("Task1|Aaa|"), TaskModel("Task2|Bbb|"), TaskModel("Task3|Ccc|"))
         var localModifiedTaskList: ArrayList<TaskModel> = arrayListOf(TaskModel("Task1|Aaa|"), TaskModel("Task2|Bbb|"), TaskModel("Task3|Ccc|"))
         var serverTaskList: ArrayList<TaskModel> = arrayListOf(TaskModel("Task1|Aaa|"), TaskModel("Task4|Ddd|"), TaskModel("Task3|Ccccccc|"))

        var taskListExpectedResult: ArrayList<TaskModel> = arrayListOf(
                TaskModel("Task1|Aaa|"),        // not modified. Keep origin
                TaskModel("Task3|Ccccccc|"),    // Modified on the server. Keep server changes
                TaskModel("Task4|Ddd|"))        // Created on the server. Keep server task

        val taskListResult = TaskConflictUtil.resolveConflict(localBaseTaskList, localModifiedTaskList, serverTaskList)  as List<TaskModel>

        // We need to sort result list, since it may contain tasks in an order different from the input
        Collections.sort(taskListResult) { t1, t2 -> t1.name.compareTo(t2.name) }

        assertEquals(taskListExpectedResult, taskListResult)
    }

    /**
     * This simulates
     *  - task2 was removed locally
     *  - task4 was created locally
     *  - task3 was changed locally
     */
    @Test
    @Throws(Exception::class)
    fun testLocalChanges() {
        
        var localBaseTaskList: ArrayList<TaskModel> = arrayListOf(TaskModel("Task1|Aaa|"), TaskModel("Task2|Bbb|"), TaskModel("Task3|Ccc|"))
        var localModifiedTaskList: ArrayList<TaskModel> = arrayListOf(TaskModel("Task1|Aaa|"), TaskModel("Task4|Ddd|"), TaskModel("Task3|Ccccccc|"))
        var serverTaskList: ArrayList<TaskModel> = arrayListOf(TaskModel("Task1|Aaa|"), TaskModel("Task2|Bbb|"), TaskModel("Task3|Ccc|"))

        var taskListExpectedResult: ArrayList<TaskModel> = arrayListOf(
                TaskModel("Task1|Aaa|"),        // not modified. Keep origin
                TaskModel("Task3|Ccccccc|"),    // Modified only in the client. Keep client changes
                TaskModel("Task4|Ddd|"))        // Created in the client. Keep it

        val taskListResult = TaskConflictUtil.resolveConflict(localBaseTaskList, localModifiedTaskList, serverTaskList)  as List<TaskModel>

        // We need to sort result list, since it may contain tasks in an order different from the input
        Collections.sort(taskListResult) { t1, t2 -> t1.name.compareTo(t2.name) }

        assertEquals(taskListExpectedResult, taskListResult)
    }

    @Test
    @Throws(Exception::class)
    fun testComplexSync() {

        var localBaseTaskList: ArrayList<TaskModel> = arrayListOf(
                TaskModel("Task1|Aaa|"),
                TaskModel("Task2|Bbb|"),
                TaskModel("Task3|Ccc|"),
                TaskModel("Task4|Ddd|"))
        var localModifiedTaskList: ArrayList<TaskModel> = arrayListOf(
                TaskModel("Task1|Aaa|"),                     // not modified
                TaskModel("Task2|Modified on the client|"),  // Modified on both client and server
                TaskModel("Task3|Modified on the client|"),  // Modified only on the client
                TaskModel("Task4|Ddd|"),                     // Modified only on the server
                TaskModel("Task5|Created on the client|"),   // Created on both client and server
                TaskModel("Task6|Created on the client|"))   // Created only on the client
        var serverTaskList: ArrayList<TaskModel> = arrayListOf(
                TaskModel("Task1|Aaa|"),                     // not modified
                TaskModel("Task2|Modified on the server|"),  // Modified on both client and server
                TaskModel("Task3|Ccc|"),                     // Modified on the client
                TaskModel("Task4|Modified on the server|"),  // Modified only on the server
                TaskModel("Task5|Created on the server|"),   // Created on both client and server
                TaskModel("Task7|Created on the server|"))   // Created only on the server

        var taskListExpectedResult: ArrayList<TaskModel> = arrayListOf(
                TaskModel("Task1|Aaa|"),                     // not modified. Keep origin
                TaskModel("Task2|Modified on the server|"),  // Modified on both client and server. Keep server changes
                TaskModel("Task3|Modified on the client|"),  // Modified only on the client. Keep client changes
                TaskModel("Task4|Modified on the server|"),  // Modified only on the server. Keep server changes
                TaskModel("Task5|Created on the server|"),   // Created on both client and server. Keep server task
                TaskModel("Task6|Created on the client|"),   // Created only on the client. Keep client task
                TaskModel("Task7|Created on the server|"))   // Created only on the server. Keep server task


        val taskListResult = TaskConflictUtil.resolveConflict(localBaseTaskList, localModifiedTaskList, serverTaskList) as List<TaskModel>

        // We need to sort result list, since it may contain tasks in an order different from the input
        Collections.sort(taskListResult) { t1, t2 -> t1.name.compareTo(t2.name) }

        assertEquals(taskListExpectedResult, taskListResult)
    }
}
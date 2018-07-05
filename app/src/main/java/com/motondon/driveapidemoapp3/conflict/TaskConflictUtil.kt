/**
 * Copyright 2014 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.motondon.driveapidemoapp3.conflict

import android.util.Log

import com.motondon.driveapidemoapp3.model.TaskModel
import java.io.*
import java.util.*
import kotlin.collections.HashMap

object TaskConflictUtil {
    private val TAG = TaskConflictUtil::class.java.simpleName

    /**
     * This method is called by the ConflictResolve::resolve() after it retrieve all three necessary lists to resolve any conflicts. They are:
     *   - localBaseTaskList
     *   - localModifiedTaskList
     *   - serverTaskList
     *
     * According to the docs, "A conflict occurs when the local version of the base file contents do not match the current file contents on
     * the server. In this case, a simple merge is not possible and a Conflict status is returned."
     *
     * Since when user requested a sync process, we called DriveResourceClient::commitContents() using ExecutionOptions::setNotifyOnCompletion(true),
     * we will be notified about any conflict detected during the sync. We also set ExecutionOptions.CONFLICT_STRATEGY_KEEP_REMOTE as a strategy that
     * means Drive keeps the remote version of the file instead of overwriting it with the locally committed changes when a conflict is detected. This
     * allows us to resolve any conflict by ourselves.
     *
     * This means it is up to us to resolve the conflicts the way we want and later send the resolution back to the Drive.
     *
     *
     * This is the strategy we defined to resolve all conflicts:
     * - 1) Tasks modified on the server will override local tasks. If the same task (i.e. a task with the same name) was modified locally, local
     *      changes will be lost.
     * - 2) Any task removed on the server will be also removed locally. If same task was modified locally, local changes will be lost.
     * - 3) Task created on the server will be created locally. In case of a task with the same name was created on both server and local, local
     *      task will be lost.
     * - 4) New local local task will be synchronized to the server (unless a task with the same name was created on the server. See item #3.
     * - 5) Tasks deleted locally will be also deleted on the server.
     * - 6) Local modification will override server content, unless the same task was removed or modified on the server. See item #1.
     *
     * Once again: the way a conflict is resolved is totally up to us. At the end, we need to return a resolved list that will be commited back on the server.
     *
     * @param localBaseTaskList local tasks before any changes locally (i.e.: before adding, removing or changing any task).
     * @param localModifiedTaskList local tasks after all changes (i.e.: tasks at the moment we requested a sync process (i.e.:any new or modified task. Deleted tasks are not here)
     * @param serverTaskList remote tasks (i.e. tasks in Drive returned by the sync process
     * @return a list with all tasks after conflict resolution
     */
    fun resolveConflict(localBaseTaskList: ArrayList<TaskModel>, localModifiedTaskList: ArrayList<TaskModel>, serverTaskList: ArrayList<TaskModel>) : ArrayList<TaskModel> {
        Log.d(TAG, "resolveConflict() - Begin")

        printTasks(localBaseTaskList, "localBaseTaskList")
        printTasks(localModifiedTaskList, "localModifiedTaskList")
        printTasks(serverTaskList, "serverTaskList")

        val finalTaskHash: HashMap<String, TaskModel> = hashMapOf()

        // Add all base tasks to the finalTasHash. Base tasks are those local tasks we had before any local modification
        localBaseTaskList.forEach { finalTaskHash[it.name] = TaskModel(it) }

        Log.d(TAG, "resolveConflict() - Added tasks from localBaseTaskList to the finalTasHash. Now it contains ${localBaseTaskList.forEach { it }}")

        // First iterates over localModifiedTaskList list to process local modifications.
        Log.d(TAG, "resolveConflict() - Iterating over localModifiedTaskList...")
        localModifiedTaskList.forEach {
            Log.d(TAG, "resolveConflict() - Checking task: ${it.name}...")

            if (!finalTaskHash.containsKey(it.name)) {
                // a task was not found in the finalTasHash? It means it is a new local task. So, add it to the finalTasHash. This is our strategy #4
                Log.d(TAG, "resolveConflict() - Detected a new local task: ${it.name}. Adding it to the finalTasHash (note if we detect later a new server task with the same name, it will have priority)...")
                finalTaskHash[it.name] = TaskModel(it)
            } else {

                // If a task in the localModifiedTaskList is already present in the finalTaskHash, compare both tasks in order to find out whether it was modified locally
                // or not. If so, add it to the finalTaskHash. Note that if it was also changed or removed on the server, local modification will be lost). This is our strategy #6
                if (finalTaskHash[it.name] != it) {
                    Log.d(TAG, "resolveConflict() - Detected local task: ${it.name} was modified. Adding it to the finalTasHash (note if it was also changed or removed on the server, local modification will be lost)...")
                    finalTaskHash[it.name] = TaskModel(it)
                }
            }
        }

        // Now it is time to iterate over the serverTaskList in order to resolve any server change.
        Log.d(TAG, "resolveConflict() - Iterating over serverTaskList...")
        serverTaskList.forEach {
            Log.d(TAG, "resolveConflict() - Checking task: ${it.name}...")

            if (!finalTaskHash.containsKey(it.name)) {
                // a task was not found in the finalTasHash? It means it is a new remote task. So, add it to the finalTasHash. This is our strategy #3
                Log.d(TAG, "resolveConflict() - Detected a new server task: ${it.name}. Adding it to the finalTasHash...")
                finalTaskHash[it.name] = TaskModel(it)

            } else {
                // As part of our strategy #1, any local change will be overwritten for server changes. But prior to overwrite it, we need to consider the case where a task was changed only
                // locally (our strategy #6). In this case we need to keep local changes. Based on that, we cannot simply copy a server task to the finalTaskHash, but check it first.
                // The easiest way is to compare server task to the equivalent in the localBaseTaskList. It they are different, override it in the finalTakHash. Once again, any local change
                // will be overwritten.
                localBaseTaskList
                    .forEach { localBaseTask ->
                        if (localBaseTask.name == it.name) {
                            if (localBaseTask != it) {
                                Log.d(TAG, "resolveConflict() - Detected task: ${it.name} was modified on the server. Adding it to the finalTasHash Any local change will be lost!")
                                finalTaskHash[it.name] = TaskModel(it)
                            }
                        }
                    }

                // If a task was created on both client and server (i.e.: a task with the same name, keep server version
                localModifiedTaskList
                    .forEach { localModifiedTask ->
                        if (localModifiedTask.name == it.name) {
                            if (localModifiedTask != it && !containsTask(localBaseTaskList, it)) {
                                Log.d(TAG, "resolveConflict() - Detected task: ${it.name} was created on both client and server. Keeping server task. Local new task will be lost!")
                                finalTaskHash[it.name] = TaskModel(it)
                            }
                        }
                    }
            }
        }

        // Now, let's iterate over finalTaskHash in order to remove from it all tasks that were removed from locally ore remotely.
        Log.d(TAG, "resolveConflict() - Checking for removal tasks...")
        val iter = finalTaskHash.iterator()
        while (iter.hasNext()) {
            val item = iter.next()

            // If a task exists in the localBaseTaskList (that is the list containing all local tasks before any change), and does not exists in the serverTaskList,
            // it means that task was removed from the server. So, remove it from our final list. This is part of our strategy #2
            if (localBaseTaskList.contains(item.value) && !serverTaskList.contains(item.value)) {
                Log.d(TAG, "resolveConflict() - Detected task: ${item.value.name} was removed on the server. Removing it from the finalTasHash...")
                iter.remove()

                // When detecting a task was removed on the server, do not check whether it was also remove locally, since it will no longer exist in the iterator.
                // In this case, if a task was removed on both server and local, it will be removed when sync back.
                continue
            }

            // If a task exists in the localBaseTaskList (that is the list containing all local tasks before any change), and does not exists in the localModifiedTaskList,
            // it means that task was removed locally. So, remove it from our final list. This is part of our strategy #5
            if (localBaseTaskList.contains(item.value) && !localModifiedTaskList.contains(item.value)) {
                Log.d(TAG, "resolveConflict() - Detected task: ${item.value.name} was removed locally. Removing it from the finalTasHash...")
                iter.remove()
            }
        }

        // Now, we should have a hash with all conflicts resolved. Create a list based on the hash and return it.
        val finalTaskList: ArrayList<TaskModel> = arrayListOf()
        finalTaskList.addAll(finalTaskHash.values)

        // Just print finalTaskList for debug purpose
        printTasks(finalTaskList, "finalTaskList")

        Log.d(TAG, "resolveConflict() - End")
        return finalTaskList
    }

    /**
     * Deserialize an inputStream into a list of TaskModel. This is used when receiving data from the Drive
     *
     * @param is InputStream used to read into TaskModel list.
     * @return List of TaskModel
     */
    fun getTaskListFromInputStream(`is`: InputStream?): ArrayList<TaskModel> {
        Log.d(TAG, "getTaskListFromInputStream() - Begin")

        val taskList: ArrayList<TaskModel> = arrayListOf()

        try {

            val reader = `is`?.bufferedReader()
            reader?.readLines()?.let {
                for(line in it){
                    Log.d(TAG, "getTaskListFromInputStream() - Reading line: $line")

                    if (line.isEmpty()) continue

                    try {
                        // This constructor will take care of split each line and fill up the task fields accordingly
                        val task = TaskModel(line)

                        Log.d(TAG, "getTaskListFromInputStream() - Adding task ${task.name} to the list")
                        taskList.add(task)
                    } catch (e: Exception) {
                        Log.w(TAG, "getTaskListFromInputStream() - Error while reading line: $line. Keep reading next lines...", e)
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "getTaskListFromInputStream() - Error while reading task list file content", e)
            throw RuntimeException("Unable to read file content.")
        }

        Log.d(TAG, "getTaskListFromInputStream() - Task list contains ${taskList.size} items. Returning it...")
        return taskList
    }

    /**
     * Convert a list of TaskModel into a string. It is used when writing a task list to the Drive.
     * The output format is something like:
     *
     * Task1|Aaa|
     * Task2|Bbb|
     * ...
     *
     */
    fun formatStringFromTaskList(tasks: List<TaskModel>): String {
        Log.d(TAG, "formatStringFromTaskList() - Begin")

        val result = StringBuilder()

        tasks.forEach { t ->
            val temp = StringBuilder()
                .append(t.name).append('|')
                .append(t.description).append('|')
                .append("\r\n")

            Log.d(TAG, "formatStringFromTaskList() - formatting line: $temp...")
            result.append(temp)
        }

        Log.d(TAG, "formatStringFromTaskList() - Returning string: $result")
        return result.toString()
    }

    /**
     * Helper method that prints each task from a given list. It prints One task per line.
     *
     */
    private fun printTasks(localBaseTaskList: List<TaskModel>, listName: String) {
        localBaseTaskList.forEach {
            Log.d(TAG, "printTasks() - $listName contains task: $it")
        }
    }

    /**
     * Helper method to search for tasks based on its name
     *
     * @param tasks
     * @param task
     * @return
     */
    private fun containsTask(tasks: List<TaskModel>, task: TaskModel): Boolean {
        tasks.forEach {
            if (it.name == task.name) {
                return true
            }
        }
        return false
    }
}


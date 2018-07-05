package com.motondon.driveapidemoapp3.adapter

import android.content.Context
import android.content.Intent
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import com.motondon.driveapidemoapp3.R
import com.motondon.driveapidemoapp3.activity.ConflictResolutionActivity
import com.motondon.driveapidemoapp3.activity.CreateUpdateTaskActivity
import com.motondon.driveapidemoapp3.common.Constants
import com.motondon.driveapidemoapp3.model.TaskModel
import kotlinx.android.synthetic.main.task_item.view.*

class TaskAdapter(private val mContext: Context, private val tasks: MutableList<TaskModel>) : RecyclerView.Adapter<TaskAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(mContext).inflate(R.layout.task_item, parent, false)

        return ViewHolder(v)
    }

    override fun getItemCount(): Int {
        return tasks.size
    }

    override fun onBindViewHolder(holder: ViewHolder?, position: Int) {
        holder?.bind(position)
    }

    fun getItem(position: Int): TaskModel? {
        return tasks[position]
    }

    fun addTask(task: TaskModel) {
        Log.d(TAG, "addTask() - task: $task")

        this.tasks.add(task)
        notifyDataSetChanged()
    }

    fun updateTask(task: TaskModel) {
        Log.d(TAG, "updateTask() - task: $task")

        tasks
            .filter { it.name == task.name }
            .map {  val index = this.tasks.indexOf(it)

                tasks[index] = task
                notifyDataSetChanged()
            }
    }

    fun deleteTask(task: TaskModel) {
        Log.d(TAG, "deleteTask() - task: $task")

        tasks
            .filter { it.name == task.name }
            .map { val index = this.tasks.indexOf(it)

                var tm = tasks[index]

                this.tasks.remove(tm)
                notifyDataSetChanged()
            }
    }

    fun setTasks(taskModels: List<TaskModel>) {
        tasks.clear()
        tasks.addAll(taskModels)
        notifyDataSetChanged()
    }

    fun getTasks() : List<TaskModel> {
        return tasks
    }

    companion object {
        private val TAG = TaskAdapter::class.java.simpleName
    }

    inner class ViewHolder(containerView: View) : RecyclerView.ViewHolder(containerView), View.OnClickListener {

        private val TAG = ViewHolder::class.java.simpleName

        private var currentTask: TaskModel? = null

        init {
            itemView.setOnClickListener(this)
        }

        fun bind(position: Int) {
            Log.d(TAG, "bind() - position: $position")

            val taskModel = getItem(position)

            setCurrentTask(taskModel)

            itemView.tvTaskName.text = taskModel?.name
            itemView.tvTaskDescription.text = taskModel?.description
        }

        private fun setCurrentTask(currentTask: TaskModel?) {
            Log.d(TAG, "bind() - currentTask: $currentTask")
            this.currentTask = currentTask
        }

        override fun onClick(view: View) {
            Log.d(TAG, "onClick()")

            Intent(mContext, CreateUpdateTaskActivity::class.java).apply {
                putExtra(Constants.TASK_ACTION, Constants.UPDATE_TASK)
                putExtra(Constants.TASK, currentTask)
                (mContext as ConflictResolutionActivity).startActivityForResult(this, Constants.UPDATE_OR_DELETE_TASK_FOR_RESULT)
            }
        }
    }
}


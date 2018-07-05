package com.motondon.driveapidemoapp3.fragments

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.widget.LinearLayoutManager

import android.util.Log
import android.view.*
import com.motondon.driveapidemoapp3.R
import com.motondon.driveapidemoapp3.activity.ConflictResolutionActivity
import com.motondon.driveapidemoapp3.activity.CreateUpdateTaskActivity
import com.motondon.driveapidemoapp3.adapter.TaskAdapter
import com.motondon.driveapidemoapp3.common.Constants
import com.motondon.driveapidemoapp3.conflict.ConflictResolver
import com.motondon.driveapidemoapp3.controller.TaskListDriveController
import com.motondon.driveapidemoapp3.model.TaskModel
import kotlinx.android.synthetic.main.fragment_conflict_resolution.*

class ConflictResolutionFragment : BaseFragment() {
    
    // Receiver used to update the RecyclerView with tasks  once conflicts have been resolved as well as right after load data from the drive.
    private lateinit var broadcastReceiver: BroadcastReceiver

    private lateinit var mAdapter: TaskAdapter

    private var taskListDriveController: TaskListDriveController? = null

    private var synced: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate()")
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
  }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        Log.d(TAG, "onCreateView()")
        super.onCreateView(inflater, container, savedInstanceState)

        return inflater.inflate(R.layout.fragment_conflict_resolution, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(TAG, "onViewCreated()")

        super.onViewCreated(view, savedInstanceState)

        // initialize the adapter
        mAdapter = TaskAdapter(mContext, ArrayList())

        mTasksListView.layoutManager = LinearLayoutManager(context)
        mTasksListView.setHasFixedSize(true)
        mTasksListView.adapter = mAdapter

        // When conflicts are resolved, update the RecyclerView with the resolved TaskModel list
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.d(TAG, "BroadcastReceiver::onReceive() - action: ${intent.action}.")

                when {
                    // Called after ConflictResolver resolve all the conflicts
                    intent.action == ConflictResolver.CONFLICT_RESOLVED -> {
                        Log.d(TAG, "BroadcastReceiver::onReceive() - Received CONFLICT_RESOLVED.")

                        val taskModelList : ArrayList<TaskModel> = intent.getSerializableExtra(ConflictResolver.CONFLICT_RESOLVED_DATA) as ArrayList<TaskModel>
                        mAdapter.setTasks(taskModelList)
                    }

                    // Called after load data from Drive
                    intent.action == Constants.TASK_LIST_LOADED -> {
                        Log.d(TAG, "BroadcastReceiver::onReceive() - Received TASK_LIST_LOADED. Updating RecyclerView...")
                        val taskModelList : ArrayList<TaskModel> = intent.getSerializableExtra(Constants.TASK_LIST) as ArrayList<TaskModel>
                        mAdapter.setTasks(taskModelList)
                    }
                }
            }
        }
    }

    override fun onStart() {
        Log.d(TAG, "onStart")

        super.onStart()
        LocalBroadcastManager.getInstance(mContext).registerReceiver(broadcastReceiver,
            IntentFilter(ConflictResolver.CONFLICT_RESOLVED))

        LocalBroadcastManager.getInstance(mContext).registerReceiver(broadcastReceiver,
                IntentFilter(Constants.TASK_LIST_LOADED))
    }

    override fun onStop() {
        Log.d(TAG, "onStop")
        super.onStop()

        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(broadcastReceiver)
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater?.inflate(R.menu.conflict_resolution_main_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.home -> {
                activity?.onBackPressed()
                return true
            }

            R.id.menu_task_create -> {
                val intent = Intent(mContext, CreateUpdateTaskActivity::class.java)
                intent.putExtra(Constants.TASK_ACTION, Constants.CREATE_TASK)
                //startActivityForResult(intent, Constants.CREATE_TASK_FOR_RESULT)
                (mContext as ConflictResolutionActivity).startActivityForResult(intent, Constants.CREATE_TASK_FOR_RESULT)
                return true
            }

            R.id.menu_task_database_sync -> {
                taskListDriveController?.requestSync()
                return true
            }

            R.id.menu_sign_out -> {
                signOut()
                activity?.onBackPressed()
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.i(TAG, "onActivityResult() - triggered on pressing Select. requestCode: $requestCode - resultCode: $resultCode - data(intent): $data")

        if (data == null) {
            return
        }

        when (requestCode) {

            Constants.CREATE_TASK_FOR_RESULT ->
                if (resultCode == Activity.RESULT_OK) {
                    val task = data.getSerializableExtra(Constants.TASK) as TaskModel

                    Log.d(TAG, "onActivityResult() - Task ${task.name} was successfully created. Add it to the adapter...")
                    showMessage("Task: ${task.name} created successfully")

                    // A new task was created? Update our adapter to reflect it.
                    mAdapter.addTask(task)
                }

            Constants.UPDATE_OR_DELETE_TASK_FOR_RESULT  ->
                if (resultCode == Activity.RESULT_OK) {
                    val task = data.getSerializableExtra(Constants.TASK) as TaskModel
                    val action = data.getStringExtra(Constants.TASK_ACTION)

                    if (action == Constants.TASK_UPDATED) {
                        Log.d(TAG, "onActivityResult() - Task ${task.name} was successfully updated. Add it to the adapter...")
                        showMessage("Task: ${task.name} updated successfully")

                        // An existent task was updated? Update our adapter to reflect it.
                        mAdapter.updateTask(task)
                    }

                    if (action == Constants.TASK_DELETED) {

                        Log.d(TAG, "onActivityResult() - Task ${task.name} was successfully deleted. Remove it from the adapter ...")
                        showMessage("Task: ${task.name} deleted successfully")

                        // Deleted a task? Update our adapter to reflect it.
                        mAdapter.deleteTask(task)
                    }
            }

            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    /**
     * This event is received right after user has signed in and the Drive client has been initialized.
     *
     */
    override fun onDriveClientReady() {
        Log.d(TAG, "onDriveClientReady()")

       taskListDriveController = TaskListDriveController(mContext, this, getDriveClient(), getDriveResourceClient())

        // Now, request a TaskModel list from Drive in order to keep up to date with server changes
        if ((!synced)) {
            Log.i(TAG, "onDriveClientReady() - Calling DriveApi::requestData() method...")
            taskListDriveController?.requestData()
            synced = true
        }
    }

    fun getTasks() : List<TaskModel> {
        return mAdapter.getTasks()
    }

    companion object {
        val TAG = ConflictResolutionFragment::class.java.simpleName
    }
}

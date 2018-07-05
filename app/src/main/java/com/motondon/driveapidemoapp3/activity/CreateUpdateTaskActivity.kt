package com.motondon.driveapidemoapp3.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.NavUtils
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.motondon.driveapidemoapp3.R
import com.motondon.driveapidemoapp3.common.Constants
import com.motondon.driveapidemoapp3.model.TaskModel
import kotlinx.android.synthetic.main.activity_create_update_task.*
import java.util.*

class CreateUpdateTaskActivity : AppCompatActivity() {

    private var mAction: String? = null
    private var mTask: TaskModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_update_task)

        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        mAction = intent.getStringExtra(Constants.TASK_ACTION)

        // Hey, please do not do it on your app. Use a const instead! :)
        if (mAction == Constants.UPDATE_TASK) {
            this.mTask = intent.getSerializableExtra(Constants.TASK) as TaskModel

            etTaskName.setText(mTask?.name)
            etTaskDescription.setText(mTask?.description)

        } else {
            this.mTask = TaskModel()
        }

        val actionBar = supportActionBar
        if (actionBar != null) {
            if (mAction == Constants.CREATE_TASK) {
                actionBar.title = "Create Task"
            } else {
                actionBar.title = "Update Task"
                etTaskName.isEnabled = false
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {

        val inflater = menuInflater
        inflater.inflate(R.menu.conflict_resolution_create_update_task_menu, menu)

        // Only show delete menu option when updating a task.
        if (mAction == Constants.CREATE_TASK) {
            menu.removeItem(R.id.menu_task_delete)
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d(TAG, "onOptionsItemSelected()")

        when (item.itemId) {
            android.R.id.home -> {
                Log.d(TAG, "onOptionsItemSelected() - Home clicked")
                setResult(Activity.RESULT_CANCELED)
                onBackPressed()
                return true
            }

            R.id.menu_confirm_create_update_task -> {
                createUpdateTask()
                return true
            }

            R.id.menu_task_delete -> {
                val builder = AlertDialog.Builder(this@CreateUpdateTaskActivity)
                builder.setMessage("Are you sure you want to delete task: ${mTask?.name}?")

                builder.setPositiveButton("Delete") { _, _ ->

                    // If user confirm action, process it.
                    Log.d(TAG, "onOptionsItemSelected::menu_task_delete - Deleting task: ${mTask?.name}...")

                    // Create a result to send back to the caller (i.e. activity which started this one)
                    val result = Bundle()
                    result.putSerializable(Constants.TASK, mTask)
                    result.putString(Constants.TASK_ACTION, Constants.TASK_DELETED)

                    // Now, send an intent as a result
                    Log.d(TAG, "createUpdateTask() - Sending result back to the activity which started this one...")
                    val intent = Intent()
                    intent.putExtras(result)
                    setResult(Activity.RESULT_OK, intent)
                    finish()
                }

                builder.setNegativeButton("Cancel") { _, _ ->
                    /* Do nothing here! */
                }

                val dialog = builder.create()
                // display dialog
                dialog.show()

                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun createUpdateTask() {
        Log.d(TAG, "createUpdateTask() - Begin")

        if (etTaskName.text.toString().isEmpty()) {
            Toast.makeText(this, "Please, fill task name field.", Toast.LENGTH_LONG).show()
            etTaskName.requestFocus()
            return
        }

        if (etTaskDescription.text.toString().isEmpty()) {
            Toast.makeText(this, "Please, fill all fields first.", Toast.LENGTH_LONG).show()
            etTaskDescription.requestFocus()
            return
        }

        Log.d(TAG, "createUpdateTask() - Creating/Updating task locally...")

        mTask?.name = etTaskName.text.toString()
        mTask?.description = etTaskDescription.text.toString()

        // Create a result to send back to the caller (i.e. activity which started this one)
        val result = Bundle()
        result.putSerializable(Constants.TASK, mTask)
        result.putString(Constants.TASK_ACTION, if (mAction == Constants.UPDATE_TASK) Constants.TASK_UPDATED else Constants.TASK_CREATED)

        // Now, send an intent as a result
        Log.d(TAG, "createUpdateTask() - Sending result back to the activity which started this one...")
        val intent = Intent()
        intent.putExtras(result)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    companion object {
        private val TAG = CreateUpdateTaskActivity::class.java.simpleName
    }
}
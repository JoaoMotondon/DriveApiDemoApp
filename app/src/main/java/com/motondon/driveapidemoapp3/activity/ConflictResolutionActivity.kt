package com.motondon.driveapidemoapp3.activity

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.MenuItem
import com.motondon.driveapidemoapp3.R
import com.motondon.driveapidemoapp3.fragments.ConflictResolutionFragment

class ConflictResolutionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_basic)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        val actionBar = supportActionBar
        actionBar?.title = "Conflict Resolution"

        var fragment: Fragment? = supportFragmentManager.findFragmentByTag(ConflictResolutionFragment.TAG)
        if (fragment == null) {
            try {
                fragment = ConflictResolutionFragment()
                fragment?.arguments = intent.extras

            } catch (e: Exception) {
                Log.e(TAG, "onCreate() - Fail when trying to create ${ConflictResolutionFragment::class.java.simpleName} fragment. Error: ${e.message}")
            }
        }
        supportFragmentManager.beginTransaction().replace(R.id.flContent, fragment, ConflictResolutionFragment.TAG).commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> onBackPressed()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.i(TAG, "onActivityResult() - requestCode: $requestCode - resultCode: $resultCode - data(intent): $data")

        // We need it in order to call ConflictResolutionFragment::onActivityResult when a task is created, updated or deleted, in order to
        // update the adapter.
        super.onActivityResult(requestCode, resultCode, data)
        supportFragmentManager.fragments.forEach { fragment -> fragment.onActivityResult(requestCode, resultCode, data) }
    }

    companion object {
        val TAG = ConflictResolutionActivity::class.java.simpleName
    }
}
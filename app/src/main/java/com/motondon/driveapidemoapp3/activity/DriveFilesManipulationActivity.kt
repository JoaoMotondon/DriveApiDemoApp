package com.motondon.driveapidemoapp3.activity

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.MenuItem
import com.motondon.driveapidemoapp3.R
import com.motondon.driveapidemoapp3.fragments.DriveFilesManipulationFragment

class DriveFilesManipulationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_basic)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        val actionBar = supportActionBar
        actionBar?.title = "Drive Files Manipulation"

        var fragment: Fragment? = supportFragmentManager.findFragmentByTag(DriveFilesManipulationFragment.TAG)
        if (fragment == null) {
            try {
                fragment = DriveFilesManipulationFragment()
                fragment?.arguments = intent.extras

            } catch (e: Exception) {
                Log.e(TAG, "onCreate() - Fail when trying to create ${DriveFilesManipulationFragment::class.java.simpleName} fragment. Error: ${e.message}")
            }
        }
        supportFragmentManager.beginTransaction().replace(R.id.flContent, fragment, DriveFilesManipulationFragment.TAG).commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> onBackPressed()
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        val TAG = DriveFilesManipulationActivity::class.java.simpleName
    }





}

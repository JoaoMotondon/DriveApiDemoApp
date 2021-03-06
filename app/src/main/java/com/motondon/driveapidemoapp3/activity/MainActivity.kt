package com.motondon.driveapidemoapp3.activity

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.motondon.driveapidemoapp3.R
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnFileFolderManipulationWithFileOpener.setOnClickListener({filesAndFolderManipulationWithFileOpenerClick()})
        btnFQueryAndListFilesAndFolders.setOnClickListener({queryAndListFilesAndFoldersClick()})
        btnDriveFolderMonitor.setOnClickListener({listenDriveRootFolderClick()})
        btnConflictResolution.setOnClickListener({conflictResolutionClick()})
    }

    private fun filesAndFolderManipulationWithFileOpenerClick() {
        val i = Intent(applicationContext, ActionsWithDriveFilePickerActivity::class.java)
        startActivity(i)
    }

    private fun queryAndListFilesAndFoldersClick() {
        val i = Intent(applicationContext, DriveFilesManipulationActivity::class.java)
        startActivity(i)
    }

    private fun listenDriveRootFolderClick() {
        val i = Intent(applicationContext, DriveFolderMonitorActivity::class.java)
        startActivity(i)
    }

    private fun conflictResolutionClick() {
        val i = Intent(applicationContext, ConflictResolutionActivity::class.java)
        startActivity(i)
    }
}

package com.motondon.driveapidemoapp3.fragments

import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import com.google.android.gms.drive.*
import com.google.android.gms.drive.events.OpenFileCallback
import com.google.android.gms.drive.query.Filters
import com.google.android.gms.drive.query.SearchableField
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks

import com.motondon.driveapidemoapp3.R
import kotlinx.android.synthetic.main.fragment_actions_with_drive_file_picker.*
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class ActionsWithDriveFilePickerFragment : BaseFragment() {

    /**
     * Tracks completion of the drive picker
     */
    private var mOpenItemTaskSource: TaskCompletionSource<DriveId>? = null

    /**
     * This event is received right after user has signed in and the Drive client has been initialized.
     *
     */
    override fun onDriveClientReady() {
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        Log.d(TAG, "onCreateView()")
        super.onCreateView(inflater, container, savedInstanceState)

        return inflater.inflate(R.layout.fragment_actions_with_drive_file_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(TAG, "onViewCreated()")

        super.onViewCreated(view, savedInstanceState)

        btn_open_file.setOnClickListener({ onClickOpenFile() })

        btn_pin_unpin_file.setOnClickListener({ onClickPinUnpinFile() })

        btn_retrieve_file_content.setOnClickListener({ onClickRetrieveFileContent() })

        btn_retrieve_file_metadata.setOnClickListener({ onClickRetrieveFileMetadata() })

        btn_count_files_in_folder.setOnClickListener({ onClickCountFilesInFolder() })
    }

    private fun onClickOpenFile() {
        Log.d(TAG, "onClickOpenFile()")

        // Request user to pick up a file from the Drive to be opened.
        startIntentSenderForResult("text/plain", REQUEST_CODE_OPENER)
    }

    private fun onClickPinUnpinFile() {
        Log.d(TAG, "onClickPinUnpinFile()")

        // Request user to choose a Drive file to be pinned/unpinned
        startIntentSenderForResult("text/plain", REQUEST_CODE_PIN_UNPIN_FILE)
    }

    private fun onClickRetrieveFileContent() {
        Log.d(TAG, "onClickRetrieveFileContent()")

        // Request user to pick up a Drive file to have its content retrieved.
        startIntentSenderForResult("text/plain", REQUEST_CODE_RETRIEVE_CONTENT)
    }

    private fun onClickRetrieveFileMetadata() {
        Log.d(TAG, "onClickRetrieveFileMetadata()")

        // Request user to pick up a Drive file in order to retrieve its metadata.
        startIntentSenderForResult("text/plain", REQUEST_CODE_RETRIEVE_FILE_METADATA)
    }

    private fun onClickCountFilesInFolder() {
        Log.d(TAG, "onClickCountFilesInFolder()")

        // Request user to choose a Drive folder in order to show the number of files/folders it contains.
        startIntentSenderForResult(DriveFolder.MIME_TYPE, REQUEST_CODE_COUNT_FILES_IN_FOLDER)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        Log.i(TAG, "in onActivityResult() - requestCode: $requestCode - resultCode: $resultCode - data(intent): $data")

        if (data == null) {
            return
        }

        when (requestCode) {

            REQUEST_CODE_OPENER -> if (resultCode == Activity.RESULT_OK) {

                val driveId = data.getParcelableExtra<DriveId>(OpenFileActivityOptions.EXTRA_RESPONSE_DRIVE_ID)
                mOpenItemTaskSource?.setResult(driveId)

                Log.i(TAG, "onActivityResult() - Trying to open file id: ${driveId.resourceId}")

                // After user has chosen a Drive file, open it by sending the ACTION_VIEW intent. File will be opened by an external app
                val url = "https://drive.google.com/open?id=" + driveId.resourceId
                val i = Intent(Intent.ACTION_VIEW)
                i.data = Uri.parse(url)
                startActivity(i)
            } else {
                mOpenItemTaskSource?.setException(RuntimeException("Unable to open file"))
            }

            REQUEST_CODE_PIN_UNPIN_FILE -> if (resultCode == Activity.RESULT_OK) {

                val mFileId = data.getParcelableExtra<DriveId>(OpenFileActivityOptions.EXTRA_RESPONSE_DRIVE_ID)
                Log.i(TAG, "onActivityResult() - Trying to pin/unpin file id: ${mFileId.resourceId}")
                val file = mFileId.asDriveFile()

                val pinFileTask = getDriveResourceClient().getMetadata(file).continueWithTask { task ->
                    val metadata = task.result
                    if (!metadata.isPinnable) {
                        showMessage("File is not pinnable")
                        return@continueWithTask Tasks.forResult(metadata)
                    }

                    // After user has chosen a Drive file, check whether it is pinned or unpinned and execute the opposite action.
                    val pin = !metadata.isPinned

                    // Create a metadata informing to pin/unpin the file
                    val changeSet = MetadataChangeSet.Builder()
                        .setPinned(pin)
                        .build()

                    // Now, request Drive to pin/unpin it
                    return@continueWithTask getDriveResourceClient().updateMetadata(file, changeSet)
                }

                // And finally check the result
                pinFileTask
                    .addOnSuccessListener { data ->

                        when {
                            data.isPinned -> {
                                Log.d(TAG, "onActivityResult() - File successfully pinned")
                                showMessage("File successfully pinned")
                            }
                            else -> {
                                Log.d(TAG, "onActivityResult() - File successfully unpinned")
                                showMessage("File successfully unpinned")
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "onActivityResult() - Unable to update metadata", e)
                        showMessage("Unable to update metadata")
                    }
            }

            REQUEST_CODE_RETRIEVE_CONTENT -> if (resultCode == Activity.RESULT_OK) {
                val mFileId = data.getParcelableExtra<DriveId>(OpenFileActivityOptions.EXTRA_RESPONSE_DRIVE_ID)
                Log.i(TAG, "onActivityResult() - Trying to retrieve content from file id: ${mFileId.resourceId}")
                val file = mFileId.asDriveFile()

                // Create a ProgressDialog object in order to lock the screen while retrieving Drive file. Note that we are not covering screen rotate. Once this happen, our
                // progress dialog will be dismissed.
                val progressDialog = ProgressDialog.show(activity, "Please wait ...", "Loading file...", true)
                progressDialog.setCancelable(false)

                val openCallback = object : OpenFileCallback() {
                    override fun onProgress(bytesDownloaded: Long, bytesExpected: Long) {
                        // Update progress dialog with the latest progress.
                        val progress = (bytesDownloaded * 100 / bytesExpected).toInt()
                        Log.d(TAG, String.format("Loading progress: %s percent", progress))
                        progressDialog.progress = progress
                    }

                    override fun onContents(driveContents: DriveContents) {
                        // onProgress may not be called for files that are already available on the device. Mark the progress as complete
                        // when contents available to ensure status is updated.
                        progressDialog.progress = 100
                        progressDialog.dismiss()

                        // Now it is time to read file content. Since we allow only text files, add its content in a string variable
                        // and shows it to the user.
                        val reader = BufferedReader(InputStreamReader(driveContents.inputStream))

                        try {
                            val allText = reader.use(BufferedReader::readText)

                            Log.i(TAG, "onActivityResult() - File content: $allText")
                            showDialog(allText)

                        } catch (e: IOException) {
                            Log.e(TAG, "onActivityResult() - IOException while reading from the stream", e)
                        }
                    }

                    override fun onError(e: Exception) {
                        progressDialog.dismiss()
                        Log.e(TAG, "onActivityResult() - Unable to read contents", e)
                        showMessage("Error while retrieving file")
                    }
                }
                getDriveResourceClient().openFile(file, DriveFile.MODE_READ_ONLY, openCallback)
            }

            REQUEST_CODE_RETRIEVE_FILE_METADATA -> if (resultCode == Activity.RESULT_OK) {
                val mFileId = data.getParcelableExtra<DriveId>(OpenFileActivityOptions.EXTRA_RESPONSE_DRIVE_ID)
                Log.i(TAG, "onActivityResult() - Trying to retrieve metadata from file id: ${mFileId.resourceId}")
                val file = mFileId.asDriveFile()

                val getMetadataTask = getDriveResourceClient().getMetadata(file)

                getMetadataTask
                    .addOnSuccessListener { metadata ->

                        // Now, present metadata to the user in an alert dialog.
                        val msg = ("Title:            " + metadata.title + "\n"
                                + "File size:        " + metadata.fileSize + "\n"
                                + "MIME type:        " + metadata.mimeType + "\n"
                                + "Created date:     " + metadata.createdDate + "\n"
                                + "Drive id:         " + metadata.driveId + "\n"
                                + "Is starred:       " + (if (metadata.isStarred) "yes" else "no") + "\n"
                                + "Is folder:        " + (if (metadata.isFolder) "yes" else "no") + "\n"
                                + "Is shared:        " + (if (metadata.isShared) "yes" else "no") + "\n"
                                + "Is pinned:        " + (if (metadata.isPinned) "yes" else "no") + "\n"
                                + "Is in app folder: " + (if (metadata.isInAppFolder) "yes" else "no") + "\n"
                                + "Is shared:        " + if (metadata.isShared) "yes" else "no")

                        Log.i(TAG, "onActivityResult() - Metadata: $msg")
                        showDialog(msg)

                        return@addOnSuccessListener
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "onActivityResult() - Unable to retrieve metadata", e)
                        showMessage("Unable to retrieve metadata")
                    }
            }

            REQUEST_CODE_COUNT_FILES_IN_FOLDER -> if (resultCode == Activity.RESULT_OK) {
                val mFileId = data.getParcelableExtra<DriveId>(OpenFileActivityOptions.EXTRA_RESPONSE_DRIVE_ID)
                Log.i(TAG, "onActivityResult() - Trying to count number of files/folder in folder id: ${mFileId.resourceId}")
                val folder = mFileId.asDriveFolder()

                // After user has chosen a folder, ask Drive for its children an show to the user how many children it has.
                getDriveResourceClient().listChildren(folder)
                    .addOnSuccessListener { listChildren ->
                        val count = listChildren.count
                        Log.i(TAG, "onActivityResult() - Folder contains $count file(s) on it")
                        showMessage("Folder contains $count file(s) on it")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "onActivityResult() - Problem while retrieving files", e)
                        showMessage("Problem while retrieving files")
                    }
            }

            else -> {
                super.onActivityResult(requestCode, resultCode, data)
            }
        }
    }

    /**
     * Prompts the user to select a folder using OpenFileActivity.
     *
     * @param mimeType
     * @param requestCode
     */
    private fun startIntentSenderForResult(mimeType: String, requestCode: Int) {
        Log.i(TAG, "startIntentSenderForResult() - mimeType $mimeType - requestCode: $requestCode")

        val openOptions = OpenFileActivityOptions.Builder()
            .setSelectionFilter(Filters.eq(SearchableField.MIME_TYPE, mimeType))
            .setActivityTitle("Select a file")
            .build()

        mOpenItemTaskSource = TaskCompletionSource()
        getDriveClient()
            .newOpenFileActivityIntentSender(openOptions)
            .continueWith({ task ->
                // Start intent
                startIntentSenderForResult(
                    task.result, requestCode, null, 0, 0, 0, null)
            })
    }

    companion object {
        val TAG = ActionsWithDriveFilePickerFragment::class.java.simpleName

        private const val REQUEST_CODE_OPENER = 101
        private const val REQUEST_CODE_PIN_UNPIN_FILE = 102
        private const val REQUEST_CODE_RETRIEVE_CONTENT = 103
        private const val REQUEST_CODE_RETRIEVE_FILE_METADATA = 104
        private const val REQUEST_CODE_COUNT_FILES_IN_FOLDER = 105
    }
}

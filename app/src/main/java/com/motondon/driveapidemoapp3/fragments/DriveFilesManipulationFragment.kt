package com.motondon.driveapidemoapp3.fragments

import android.app.Activity
import android.app.ProgressDialog
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.support.v4.content.LocalBroadcastManager
import android.support.v4.util.Pair
import android.support.v7.widget.GridLayoutManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.android.gms.drive.*

import com.google.android.gms.drive.query.Filters
import com.google.android.gms.drive.query.Query
import com.google.android.gms.drive.query.SearchableField
import com.google.android.gms.drive.query.SortOrder
import com.google.android.gms.drive.query.SortableField
import com.google.android.gms.tasks.Tasks
import com.motondon.driveapidemoapp3.R
import com.motondon.driveapidemoapp3.adapter.FileFolderAdapter
import com.motondon.driveapidemoapp3.common.Constants
import kotlinx.android.synthetic.main.fragment_drive_files_manipulation.*
import java.io.*

import java.lang.reflect.InvocationTargetException
import java.util.ArrayList
import java.util.Random

class DriveFilesManipulationFragment : BaseFragment(), FileFolderAdapter.FileFolderAdapterInterface {

    /**
     * This event is received right after user has signed in and the Drive client has been initialized.
     *
     */
    override fun onDriveClientReady() {
    }

    // Receiver used to update the file RecyclerView when a new file is created on the server
    private lateinit var broadcastReceiver: BroadcastReceiver

    // Adapter to define how files and folders are displayed in the ListView.
    private var mAdapter: FileFolderAdapter? = null

    internal inner class SpinnerOptionAndMethodName(first: String, second: String) : Pair<String, String>(first, second) {
        override fun toString(): String {
            return first?.let { it } ?: ""
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        Log.d(TAG, "onCreateView()")
        super.onCreateView(inflater, container, savedInstanceState)
        return inflater.inflate(R.layout.fragment_drive_files_manipulation, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(TAG, "onViewCreated()")

        super.onViewCreated(view, savedInstanceState)

        // Fill spinner view up with all available test names and related method names. We will use reflection to call a method based on the user choice
        val testOptions = ArrayList<SpinnerOptionAndMethodName>()
        testOptions.add(SpinnerOptionAndMethodName("Query files sorted by name", "onClickQuerySortedFilesByName"))
        testOptions.add(SpinnerOptionAndMethodName("Query files sorted by creation date", "onClickQuerySortedFilesByCreationDate"))
        testOptions.add(SpinnerOptionAndMethodName("Query files different from text plain type", "onClickQueryNoTextFilesAction"))
        testOptions.add(SpinnerOptionAndMethodName("Query text plain or html files", "onClickQueryPlainTextOrHtmlFilesAction"))
        testOptions.add(SpinnerOptionAndMethodName("Query files containing letter 'a' in the name", "onClickQueryFilesWithLetter_a_inTheName"))
        testOptions.add(SpinnerOptionAndMethodName("Create a file", "onClickCreateFile"))
        testOptions.add(SpinnerOptionAndMethodName("Upload local file to Drive", "onClickUploadLocalFileToDrive"))

        sTestOptions.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                spinnerTestOptionsItemSelected(position)
            }
        }

        // Initialize the spinner adapter
        val adapter = ArrayAdapter(
                mContext, android.R.layout.simple_spinner_item, testOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sTestOptions.adapter = adapter

        // Initialize the FileFolderAdapter
        mAdapter = FileFolderAdapter(mContext, this, ArrayList())
        mFileFolderListView.setHasFixedSize(true)
        mFileFolderListView.layoutManager = GridLayoutManager(mContext, 1)
        mFileFolderListView.adapter = mAdapter

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                // This message is received when a file/folder we requested to be created was in fact created on the Drive. It is fired by
                // the DriveCompletionEventService class
                if (intent.action == Constants.FILE_CREATED_SUCCESSFULLY) {
                    Log.d(TAG, "BroadcastReceiver::onReceive() - Received FILE_CREATED_SUCCESSFULLY. Updating RecyclerView...")

                    val eventDriveId: DriveId = intent.getParcelableExtra(Constants.EVENT_DRIVE_ID)

                    // Just requesting metadata so we can get the file/folder name which we want to monitor
                    getDriveResourceClient().getMetadata(eventDriveId.asDriveResource())
                        .addOnSuccessListener { metadata ->

                            // Update RecyclerView with the task just created on the Drive.
                            onClickQuerySortedFilesByCreationDate()
                            showMessage("File ${metadata.title} was created/uploaded successfully")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "A file was created/uploaded successfully, but could not retrieve its name", e)
                            showMessage("A file was created/uploaded successfully, but could not retrieve its name")
                        }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(mContext).registerReceiver(broadcastReceiver,
            IntentFilter(Constants.FILE_CREATED_SUCCESSFULLY))
    }

    override fun onStop() {
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(broadcastReceiver)
        super.onStop()
    }

    private fun spinnerTestOptionsItemSelected(position: Int) {
        Log.d(TAG, "spinnerTestOptionsItemSelected() - position: $position")

        val testItem = sTestOptions.adapter.getItem(position) as SpinnerOptionAndMethodName

        try {
            // Instantiate an object of type method that returns a method name we will invoke
            val m = this.javaClass.getDeclaredMethod(testItem.second)

            // Now, invoke method user selected
            m.invoke(this)

        } catch (e: NoSuchMethodException) {
            Log.e(TAG, "spinnerTestOptionsItemSelected() - NoSuchMethodException: ${e.message}")
        } catch (e: InvocationTargetException) {
            Log.e(TAG, "spinnerTestOptionsItemSelected() - InvocationTargetException: ${e.message}")
        } catch (e: IllegalAccessException) {
            Log.d(TAG, "spinnerTestOptionsItemSelected() - IllegalAccessException: ${e.message}")
        }
    }

    /**
     * As the name implies, it will query Drive files order by its name (Title)
     *
     */
    private fun onClickQuerySortedFilesByName() {
        Log.d(TAG, "onClickQuerySortedFilesByName()")

        val sortOrder = SortOrder.Builder()
            .addSortAscending(SortableField.TITLE)
            .build()
        val query = Query.Builder()
            .setSortOrder(sortOrder)
            .build()

        getDriveResourceClient()
            .query(query)
            .addOnSuccessListener { sortedFileList ->
                processResult(sortedFileList)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "onClickQuerySortedFilesByName() = Error retrieving a sorted list file", e)
                showMessage("Problem while retrieving a sorted list file")
            }
    }

    /**
     * Query Drive files order by its creation date descending.
     *
     */
    private fun onClickQuerySortedFilesByCreationDate() {
        Log.d(TAG, "onClickQuerySortedFilesByCreationDate()")

        val sortOrder = SortOrder.Builder()
            .addSortDescending(SortableField.MODIFIED_DATE)
            .build()
        val query = Query.Builder()
            .setSortOrder(sortOrder)
            .build()

        getDriveResourceClient()
            .query(query)
            .addOnSuccessListener { sortedFileList ->
                processResult(sortedFileList)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "onClickQuerySortedFilesByCreationDate() - Error retrieving a sorted list file by creation date", e)
                showMessage("Problem while retrieving a sorted list file by creation date")
            }
    }

    /**
     * This method uses Filters to filter only 'text/plain' MIME TYPE files.
     *
     */
    fun onClickQueryTextPlainAction() {
        Log.d(TAG, "onClickQueryTextPlainAction()")

        val query = Query.Builder()
            .addFilter(Filters.not(Filters.eq(SearchableField.MIME_TYPE, "text/plain")))
            .build()

        getDriveResourceClient()
            .query(query)
            .addOnSuccessListener { sortedFileList ->
                processResult(sortedFileList)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "onClickQueryTextPlainAction() - Error retrieving a list of text file ", e)
                showMessage("Problem while retrieving a list of text file ")
            }
    }

    /**
     * This method uses Filters to filter OUT 'text/plain' MIME TYPE files.
     *
     */
    fun onClickQueryNoTextFilesAction() {
        Log.d(TAG, "onClickQueryNoTextFilesAction()")

        val query = Query.Builder()
            .addFilter(Filters.not(Filters.eq(SearchableField.MIME_TYPE, "text/plain")))
            .build()

        getDriveResourceClient()
            .query(query)
            .addOnSuccessListener { sortedFileList ->
                processResult(sortedFileList)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "onClickQueryNoTextFilesAction() - Error retrieving a list of non text files", e)
                showMessage("Problem while retrieving a list of non text file ")
            }
    }

    /**
     * This method shows ho to use multiple filters in a Drive query
     *
     */
    fun onClickQueryPlainTextOrHtmlFilesAction(connectionHint: Bundle) {
        Log.d(TAG, "onClickQueryPlainTextOrHtmlFilesAction()")

        val query = Query.Builder()
            .addFilter(Filters.or(
                Filters.eq(SearchableField.MIME_TYPE, "text/html"),
                Filters.eq(SearchableField.MIME_TYPE, "text/plain")))
            .build()

        getDriveResourceClient()
            .query(query)
            .addOnSuccessListener { sortedFileList ->
                processResult(sortedFileList)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "onClickQueryPlainTextOrHtmlFilesAction() - Error retrieving a list of files using multiple filters", e)
                showMessage("Problem while retrieving a list of files using multiple filters")
            }
    }

    /**
     * This example shows how to query for Drive files by using a filter in the TITLE attribute. This approach could be interesting
     * when using searchView in order to query Drive for files based on the name typed by the user in that view.
     *
     * In that case, every new characters typed by the user, would generate a server request, so it'd better to prevent it by using
     * a strategy such as RxJava debounce.
     *
     */
    fun onClickQueryFilesWithLetter_a_inTheName() {
        Log.d(TAG, "onClickQueryFilesWithLetter_a_inTheName()")

        val query = Query.Builder()
            .addFilter(Filters.contains(SearchableField.TITLE, "a"))
            .build()

        getDriveResourceClient()
            .query(query)
            .addOnSuccessListener { sortedFileList ->
                processResult(sortedFileList)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "onClickQueryFilesWithLetter_a_inTheName() - Error retrieving a list of files with letter [a] in the title", e)
                showMessage("Problem while retrieving a list of files with letter [a] in the title")
            }
    }

    /**
     * This method creates a file in the Drive root folder. Note even when it returns success, it only means file was created in the Drive client.
     * When such file is really created on the Drive backend, a CompletionEvent.STATUS_SUCCESS event will be received.
     *
     */
    fun onClickCreateFile() {
        Log.d(TAG, "onClickCreateFile()")

        val rootFolderTask = getDriveResourceClient().rootFolder
        val createContentsTask = getDriveResourceClient().createContents()

        // Create a file with a random name.
        val newFileName = "Test_File_" + Random().nextInt(200)

        Tasks.whenAll(rootFolderTask, createContentsTask)
            .continueWithTask<DriveFile> { _ ->
                val parent = rootFolderTask.result
                val contents = createContentsTask.result
                val outputStream = contents.outputStream

                // Write some content to DriveContents
                OutputStreamWriter(outputStream).use { writer -> writer.write("Hey, this is my file!") }

                // Now add metadata to the file we want to create.
                val changeSet = MetadataChangeSet.Builder()
                    .setTitle(newFileName)
                    .setMimeType("text/plain")
                    .setStarred(true)
                    .build()

                // Add Completion notification so we can be notified when this file is created on the server. When it happens, DriveCompletionEventService will fire an
                // event. See link below for details:
                // - https://stackoverflow.com/questions/22874657/unpredictable-result-of-driveid-getresourceid-in-google-drive-android-api/31553269#31553269
                // We should rely only when receiving events from the DriveCompletionEventService
                val executionOptions = ExecutionOptions.Builder()
                    .setNotifyOnCompletion(true)
                    .build()

                // And finally request Drive to create a file in the root folder
                return@continueWithTask getDriveResourceClient().createFile(parent, changeSet, contents, executionOptions)
            }
            .addOnSuccessListener { _ ->

                // Note addOnSuccessListener event is fired when the file is created locally (i.e. in the Drive client). We need to wait
                // DriveCompletionEventService receive the Completion event so that we can ensure such file was created on the server accordingly.
                Log.d(TAG, "onClickCreateFile() - File $newFileName created successfully in the Drive client. Waiting for Completion event...")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "onClickCreateFile() - Unable to create file: $newFileName", e)
                showMessage("Failure while creating a new file")
            }
    }

    /**
     * Request user to select a local file to be upload to the Drive.
     *
     */
    private fun onClickUploadLocalFileToDrive() {
        Log.d(TAG, "onClickUploadLocalFileToDrive()")

        Intent(Intent.ACTION_GET_CONTENT).apply {

            type = "image/jpeg" // Just adding a jpeg filter.
            addCategory(Intent.CATEGORY_OPENABLE)

            try {
                startActivityForResult(
                    Intent.createChooser(this, "Select a File to Upload"),
                    FILE_SELECT_CODE)
            } catch (ex: android.content.ActivityNotFoundException) {
                // Not a file manager installed in the current device? Inform ser about it.
                showMessage("Please install a File Manager.")
            }
        }
    }

    /**
     * See this: https://developers.google.com/drive/android/trash
     *
     * When long clicking over a file (or folder), this method is called in order to send it to the trash.
     *
     * @param     metadata
     */
    override fun moveFileFolderToTrash(metadata: Metadata) {
        Log.d(TAG, "moveFileFolderToTrash() - metadata: ${metadata.title}")

        // If a DriveResource is a folder it will only be trashed if all of its children
        // are also accessible to this app.
        if (metadata.isTrashable) {

            val driveResource = metadata.driveId.asDriveResource()

            if (!metadata.isTrashed) {
                getDriveResourceClient().trash(driveResource)
                    .addOnSuccessListener { _ ->
                        onClickQuerySortedFilesByName()
                        showMessage("File/Folder " + metadata.title + " was trashed successfully")
                    }
                    .addOnFailureListener { exception ->
                        val msg = "Unable to update DriveResource trash status. Note that if the resource is a folder the trash/untrash will only be successful if all children of the folder are accessible by this user and is not app data."
                        Log.e(TAG, msg, exception)
                        showMessage(msg)
                    }
            }

        } else {
            val msg = "Resource is not trashable by your app. Resources are only trashable if the authenticated user owns the resource and the resource is not app data."
            Log.d(TAG, msg)
            showMessage(msg)
        }
    }

    override fun openFile(currentItem: Metadata?) {
        val url = "https://drive.google.com/open?id=" + currentItem?.driveId?.resourceId

        Log.d(TAG, "openFile() - file: ${currentItem?.title} - url:$url")

        Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(url)
            startActivity(this)
        }
    }

    /**
     * This method updates the adapter with the response received from the Drive
     *
     * @param metadataBuffer
     */
    private fun processResult(metadataBuffer: MetadataBuffer) {
        Log.d(TAG, "processResult()")

        mAdapter?.clear()

        val children = ArrayList<Metadata>()
        try {

            metadataBuffer.forEach{metadata ->
                if (!metadata.isTrashed) {
                    children.add(metadata.freeze())
                }
            }

            // Add just received Drive files to our adapter
            mAdapter?.setFiles(children)

        } finally {
            metadataBuffer.release()
        }
    }

    /**
     * Upload a local file user chosen to the Drive root folder. Note even when it returns success, it only means file was created in the Drive client.
     * When such file is really created on the server, a CompletionEvent.STATUS_SUCCESS event will be received.
     *
     */
    private fun saveFileToDrive(fileUri: Uri) {
        Log.d(TAG, "saveFileToDrive() - fileUri: $fileUri.")

        // Create a ProgressDialog object in order to look screen while uploading file. Note that we are not covering screen rotate. Once this happen, our
        // progress dialog will be dismissed.
        val progressDialog = ProgressDialog.show(activity, "Please wait ...", "Uploading Image ...", true)
        progressDialog.setCancelable(false)

        val rootFolderTask = getDriveResourceClient().rootFolder
        val createContentsTask = getDriveResourceClient().createContents()

        Tasks.whenAll(rootFolderTask, createContentsTask)
            .continueWithTask<DriveFile> { _ ->
                val parent = rootFolderTask.result
                val contents = createContentsTask.result
                val outputStream = contents.outputStream

                // Write the bitmap data from it.
                val options = BitmapFactory.Options()
                options.inPreferredConfig = Bitmap.Config.ARGB_8888

                try {
                    val mBitmap = MediaStore.Images.Media.getBitmap(activity?.contentResolver, fileUri)

                    val bitmapStream = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG, 100, bitmapStream)

                    outputStream.write(bitmapStream.toByteArray())
                } catch (e1: IOException) {
                    Log.e(TAG, "saveFileToDrive() - Unable to write file contents.")
                }

                // Do this only if contentResolver is not null
                activity?.contentResolver?.let {
                    // Get the original file name. It will be used to upload file using the same name.
                    val fileName = queryName(it, fileUri)
                    Log.i(TAG, "saveFileToDrive() - Uploading file: $fileName to the Drive...")

                    // Create the initial metadata - MIME type and title.
                    // For title, use original file name.
                    val metadataChangeSet = MetadataChangeSet.Builder()
                        .setMimeType("image/jpeg").setTitle(fileName).build()

                    // Add Completion notification so we can be notified when this file is created on the server. When it happens, DriveCompletionEventService will fire an
                    // event. See link below for details:
                    // - https://stackoverflow.com/questions/22874657/unpredictable-result-of-driveid-getresourceid-in-google-drive-android-api/31553269#31553269
                    // We should rely only when receiving events from the DriveCompletionEventService
                    val executionOptions = ExecutionOptions.Builder()
                        .setNotifyOnCompletion(true)
                        .build()

                    // Now request Drive to create a file in the root folder
                    return@continueWithTask getDriveResourceClient().createFile(parent, metadataChangeSet, contents, executionOptions)
                        .addOnSuccessListener { _ ->
                            progressDialog.dismiss()

                            // Note addOnSuccessListener event is fired when the file is created locally (i.e. in the Drive client). We need to wait
                            // DriveCompletionEventService receive the Completion event so that we can ensure such file was created on the server accordingly.
                            Log.d(TAG, "saveFileToDrive() - File $fileName successfully in the Drive client. Waiting for Completion event...")
                        }
                        .addOnFailureListener { e ->
                            progressDialog.dismiss()
                            Log.e(TAG, "saveFileToDrive() - Failure while trying to upload [$fileName] file", e)
                            showMessage("Failure while trying to upload [$fileName] file")
                        }
                }
            }
    }

    /**
     * Code downloaded from: http://stackoverflow.com/questions/5568874/how-to-extract-the-file-name-from-uri-returned-from-intent-action-get-content
     *
     * @param resolver
     * @param uri
     * @return
     */
    private fun queryName(resolver: ContentResolver, uri: Uri): String {
        Log.d(TAG, "queryName() - uri: $uri")

        val returnCursor = resolver.query(uri, null, null, null, null)
        val nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        returnCursor.moveToFirst()
        val name = returnCursor.getString(nameIndex)
        returnCursor.close()
        return name
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "onActivityResult() - triggered on pressing Select. requestCode: $requestCode - resultCode: $resultCode - data(intent): $data")

        if (data == null) {
            return
        }

        when (requestCode) {
            FILE_SELECT_CODE -> if (resultCode == Activity.RESULT_OK) {
                // Get the Uri of the selected file
                val uri = data.data
                Log.d(TAG, "File Uri: $uri")

                saveFileToDrive(uri)
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    companion object {
        val TAG = DriveFilesManipulationFragment::class.java.simpleName
        private const val FILE_SELECT_CODE = 0
    }
}

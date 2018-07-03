package com.motondon.driveapidemoapp3.fragments

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.app.AlertDialog
import android.util.Log
import android.widget.Toast
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.Scope
import com.google.android.gms.drive.Drive
import com.google.android.gms.drive.DriveClient
import com.google.android.gms.drive.DriveResourceClient
import java.util.HashSet

abstract class BaseFragment : Fragment() {

    // Called after the user has signed in and the Drive client has been initialized.
    protected abstract fun onDriveClientReady()



    // Handles high-level drive functions like sync
    private var mDriveClient: DriveClient? = null

    // Handle access to Drive resources/files
    private var mDriveResourceClient: DriveResourceClient? = null

    protected lateinit var mContext : Context

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate()")

        super.onCreate(savedInstanceState)

        mContext = activity

        // We could check here whether play services is installed and up to date and prevent app to run in case of something not satisfied
        Log.i(TAG, "onCreate() - Is Google Play Services available and up to date? ${GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)}")

        Log.d(TAG, "onStart() - calling buildGoogleSignInClient()...")
        buildGoogleSignInClient()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "onActivityResult() - requestCode: $requestCode - resultCode: $resultCode")

        when (requestCode) {
            REQUEST_CODE_SIGN_IN -> {
                if (resultCode != Activity.RESULT_OK) {
                    // Sign-in may fail or be cancelled by the user. For this sample, sign-in is
                    // required and is fatal. For apps where sign-in is optional, handle
                    // appropriately
                    Log.e(TAG, "Sign-in failed.")
                    activity?.finish()
                    return
                }

                val getAccountTask = GoogleSignIn.getSignedInAccountFromIntent(data)
                if (getAccountTask.isSuccessful) {
                    initializeDriveClient(getAccountTask.result)
                } else {
                    Log.e(TAG, "Sign-in failed.")
                    activity?.finish()
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    /**
     * Starts the sign-in process and initializes the Drive client.
     *
     * See link below for details;
     *   - https://developers.google.com/drive/android/auth#connecting_and_authorizing_the_google_drive_android_api
     *
     */
    private fun buildGoogleSignInClient() {
        Log.d(TAG, "buildGoogleSignInClient()")

        val requiredScopes = HashSet<Scope>(2).apply {
            add(Drive.SCOPE_FILE)
            add(Drive.SCOPE_APPFOLDER)
        }

        val signInAccount = GoogleSignIn.getLastSignedInAccount(mContext)
        if (signInAccount != null && signInAccount.grantedScopes.containsAll(requiredScopes)) {
            Log.d(TAG, "buildGoogleSignInClient() - Found a signInAccount. Finishing the sign-in process...")
            initializeDriveClient(signInAccount)

        } else {
            Log.d(TAG, "buildGoogleSignInClient() - No signInAccount. Get a GoogleSignInClient object first and then calling startActivityForResult.")

            val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestScopes(Drive.SCOPE_FILE)
                .requestScopes(Drive.SCOPE_APPFOLDER)
                .build()
            val googleSignInClient = GoogleSignIn.getClient(mContext, signInOptions)
            startActivityForResult(googleSignInClient.signInIntent, REQUEST_CODE_SIGN_IN)
        }
    }

    /**
     * Continues the sign-in process, initializing the Drive clients with the current
     * user's account.
     *
     */
    private fun initializeDriveClient(signInAccount: GoogleSignInAccount) {
        Log.d(TAG, "buildGoogleSignInClient() - Found a signInAccount. Get client.")

        mDriveClient = Drive.getDriveClient(mContext, signInAccount)
        mDriveResourceClient = Drive.getDriveResourceClient(mContext, signInAccount)
        onDriveClientReady()
    }

    protected fun getDriveClient(): DriveClient {
        // Notice it this method is called before Drive connection is established, we will get a NPE!
        // Maybe it`d be better to return a DriveClient? instead and check for nullity inside the callers
        return mDriveClient!!
    }

    protected fun getDriveResourceClient(): DriveResourceClient {
        // Notice it this method is called before Drive connection is established, we will get a NPE!
        // Maybe it`d be better to return a DriveResourceClient? instead and check for nullity inside the callers
        return mDriveResourceClient!!
    }

    protected fun showMessage(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }

    fun showDialog(message: String) {
        activity?.runOnUiThread {
             AlertDialog.Builder(mContext).apply {
                setMessage(message)
                setCancelable(false)
                setPositiveButton("OK") { dlg, _ -> dlg.dismiss() }
                create().show()
            }
        }
    }

    companion object {
        private val TAG = BaseFragment::class.java.simpleName
        private const val REQUEST_CODE_SIGN_IN = 0
    }
}

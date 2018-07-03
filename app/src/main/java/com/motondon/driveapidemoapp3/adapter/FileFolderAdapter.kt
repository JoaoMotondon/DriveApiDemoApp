package com.motondon.driveapidemoapp3.adapter

import android.content.Context
import android.support.v7.app.AlertDialog
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.drive.Metadata
import com.motondon.driveapidemoapp3.R
import kotlinx.android.synthetic.main.resource_item.view.*

class FileFolderAdapter(private val mContext: Context, private val mListener: FileFolderAdapterInterface, private var mFileMetadata: MutableList<Metadata>) : RecyclerView.Adapter<FileFolderAdapter.ViewHolder>() {

    private var enabled: Boolean = false

    interface FileFolderAdapterInterface {
        fun moveFileFolderToTrash(currentItem: Metadata)
        fun openFile(currentItem: Metadata?)
    }

    init {
        enabled = true
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(mContext).inflate(R.layout.resource_item, parent, false)

        return ViewHolder(v)
    }

    override fun getItemCount(): Int {
        return mFileMetadata.size
    }

    override fun onBindViewHolder(holder: ViewHolder?, position: Int) {
        holder?.bind(position)
    }

    fun getItem(position: Int): Metadata {
        return mFileMetadata[position]
    }

    fun setFiles(fileMetadata: MutableList<Metadata>) {
        this.mFileMetadata = fileMetadata
        notifyDataSetChanged()
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun clear() {
        this.mFileMetadata.clear()
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnCreateContextMenuListener, MenuItem.OnMenuItemClickListener, View.OnClickListener {
        private val TAG = ViewHolder::class.java.simpleName

        private lateinit var currentItem: Metadata


        init {
            // Add a mListener to show the context menu
            itemView.setOnClickListener(this)
            itemView.setOnCreateContextMenuListener(this)
        }

        fun bind(position: Int) {
            val metadata = getItem(position)

            setCurrentItem(metadata)

            itemView.filenameTextView.text =  metadata.title

            if (metadata.isFolder) itemView.imageView.setImageDrawable(mContext.resources
                    .getDrawable(R.drawable.ic_folder_black_18dp)) else {
                itemView.imageView.setImageDrawable(mContext.resources
                        .getDrawable(R.drawable.ic_description_black_18dp))

                itemView.filesizeTextView.text = "Size: " + metadata.fileSize + " bytes"
            }
        }

        override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
            menu.add(0, R.id.menu_move_to_trash, 0, "Move to trash").setOnMenuItemClickListener(this)
        }

        override fun onMenuItemClick(item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.menu_move_to_trash -> {
                    if (currentItem.isFolder) {
                        Log.d(TAG, "onMenuItemClick() - Moving folder: ${currentItem.title} to trash...")
                    } else {
                        Log.d(TAG, "onMenuItemClick() - Moving file: ${currentItem.title}  to trash...")
                    }
                    showMoveToTrashDialog()
                    true
                }

                else -> false
            }
        }

        private fun showMoveToTrashDialog() {
            AlertDialog.Builder(mContext).apply {
                if (currentItem.isFolder) {
                    setMessage("By moving a folder to trash, all its items will also be moved. Are you sure you want to move this folder to trash?")

                } else {
                    setMessage("Are you sure you want to move this file to trash?")
                }

                setPositiveButton("Move to Trash") { _, _ ->
                    // If user confirm action, process it.
                    Log.d(TAG, "showMoveToTrashDialog() - Moving file/folder: ${currentItem.title}  to trash...")
                    moveFileFolderToTrash(currentItem)
                }

                setNegativeButton("Cancel") { _, _ ->
                    /* Do nothing here! */
                }

                val dialog = create()
                // display dialog
                dialog.show()
            }
        }

        private fun moveFileFolderToTrash(currentItem: Metadata) {
            mListener.moveFileFolderToTrash(currentItem)
        }

        private fun setCurrentItem(currentItem: Metadata) {
            this.currentItem = currentItem
        }

        override fun onClick(view: View) {

            val metadata = getItem(adapterPosition)
            if (metadata.isFolder) {
                setEnabled(false)
            }

            mListener.openFile(currentItem)
        }
    }
}


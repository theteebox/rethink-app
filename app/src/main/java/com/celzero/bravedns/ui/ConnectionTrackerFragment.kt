/*
Copyright 2020 RethinkDNS and its authors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.celzero.bravedns.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.ConnectionTrackerAdapter
import com.celzero.bravedns.database.AppDatabase
import com.celzero.bravedns.util.Constants.Companion.LOG_TAG
import com.celzero.bravedns.viewmodel.ConnectionTrackerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


/**
 * Connection Tracker - Network Monitor.
 * All the network activities are captured and stored in the database.
 * Live data is used to fetch the network details from the database.
 * Database table name - ConnectionTracker.
 */
class ConnectionTrackerFragment : Fragment(), SearchView.OnQueryTextListener {

    private var recyclerView: RecyclerView? = null
    private var layoutManager: RecyclerView.LayoutManager? = null
    private var editSearchView: SearchView? = null
    private lateinit var filterIcon: ImageView
    private lateinit var deleteIcon: ImageView
    private var recyclerAdapter: ConnectionTrackerAdapter? = null
    private val viewModel: ConnectionTrackerViewModel by viewModels()
    private lateinit var filterValue: String
    private var checkedItem = 1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreate(savedInstanceState)
        return inflater.inflate(R.layout.activity_connection_tracker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView(view)
    }

    companion object{
        fun newInstance() = ConnectionTrackerFragment()
    }

    private fun initView(view: View) {
        val includeView = view.findViewById<View>(R.id.connection_list_scroll_list)
        recyclerView = includeView.findViewById<View>(R.id.recycler_connection) as RecyclerView
        editSearchView = includeView.findViewById(R.id.connection_search)
        filterIcon = includeView.findViewById(R.id.connection_filter_icon)
        deleteIcon = includeView.findViewById(R.id.connection_delete_icon)


        recyclerView!!.setHasFixedSize(true)
        layoutManager = LinearLayoutManager(requireContext())
        recyclerView!!.layoutManager = layoutManager

        ConnectionTrackerViewModel.setContext(requireContext())

        recyclerAdapter = ConnectionTrackerAdapter(requireContext())
        viewModel.connectionTrackerList.observe(viewLifecycleOwner, androidx.lifecycle.Observer(recyclerAdapter!!::submitList))
        recyclerView!!.adapter = recyclerAdapter

        editSearchView!!.setOnQueryTextListener(this)
        editSearchView!!.setOnClickListener {
            editSearchView!!.requestFocus()
            editSearchView!!.onActionViewExpanded()
        }

        filterIcon.setOnClickListener {
            showDialogForFilter()
        }

        deleteIcon.setOnClickListener {
            showDialogForDelete()
        }


    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        viewModel.setFilter(query!!)
        return true
    }

    override fun onQueryTextChange(query: String?): Boolean {
        viewModel.setFilter(query!!)
        return true
    }

    private fun showDialogForFilter() {

        val singleItems = arrayOf("Blocked connections", "All connections")

        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Select filter")

        // Single-choice items (initialized with checked item)
        builder.setSingleChoiceItems(singleItems, checkedItem) { dialog, which ->
            // Respond to item chosen
            filterValue = if (which == 0)
                "isFilter"
            else
                ""
            checkedItem = which
            if (HomeScreenActivity.GlobalVariable.DEBUG) Log.d(LOG_TAG, "Filter Option selected: $filterValue")
            viewModel.setFilterBlocked(filterValue)
            dialog.dismiss()
        }
        builder.show()

    }


    private fun showDialogForDelete() {
        val builder = AlertDialog.Builder(requireContext())
        //set title for alert dialog
        builder.setTitle(R.string.conn_track_clear_logs_title)
        //set message for alert dialog
        builder.setMessage(R.string.conn_track_clear_logs_message)
        builder.setIcon(android.R.drawable.ic_dialog_alert)
        builder.setCancelable(true)
        //performing positive action
        builder.setPositiveButton("Delete logs") { _, _ ->
            GlobalScope.launch(Dispatchers.IO) {
                val mDb = AppDatabase.invoke(ConnectionTrackerViewModel.contextVal.applicationContext)
                val connectionTrackerDAO = mDb.connectionTrackerDAO()
                connectionTrackerDAO.clearAllData()
            }
        }

        //performing negative action
        builder.setNegativeButton("Cancel") { _, _ ->
        }
        // Create the AlertDialog
        val alertDialog: AlertDialog = builder.create()
        // Set other dialog properties
        alertDialog.setCancelable(true)
        alertDialog.show()
    }
}

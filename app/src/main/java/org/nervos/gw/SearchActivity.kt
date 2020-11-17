package org.nervos.gw

import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.nervos.gw.utils.HistoryPref

class SearchActivity : AppCompatActivity() {

    private var histories: List<String>? = null
    private var historyPref: HistoryPref? = null
    private var recyclerView: RecyclerView? = null
    private var viewAdapter: RecyclerView.Adapter<*>? = null
    private var viewManager: RecyclerView.LayoutManager? = null
    private var inputEdit: EditText? = null
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        historyPref = HistoryPref(this)
        histories = historyPref!!.getHistoryUrls()
        if (histories != null && histories!!.isNotEmpty()) {
            viewManager = LinearLayoutManager(this)
            viewAdapter = HistoryAdapter(histories!!)
            recyclerView = findViewById<RecyclerView>(R.id.search_recycle).apply {
                setHasFixedSize(true)
                layoutManager = viewManager
                adapter = viewAdapter
            }
        }

        webView = findViewById(R.id.search_web_view)
        webView?.settings?.javaScriptEnabled = true
        webView?.webViewClient = SearchWebViewClient()
        inputEdit = findViewById<EditText>(R.id.search_input)
        inputEdit?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                recyclerView?.visibility = View.GONE
                webView?.visibility = View.VISIBLE
                webView?.loadUrl(s.toString())
            }
        })
    }

    private class SearchWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
           return false
        }
    }

    class HistoryAdapter(private val histories: List<String>) :
        RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            var historyUrl: TextView? = null
            init {
                historyUrl = view.findViewById(R.id.item_history_url)
            }
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.item_history, viewGroup, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            viewHolder.historyUrl?.text = histories[position]
        }

        override fun getItemCount() = histories.size

    }

}
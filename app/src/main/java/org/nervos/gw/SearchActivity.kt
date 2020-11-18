package org.nervos.gw

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.nervos.gw.passport.AddPassportActivity
import org.nervos.gw.utils.HistoryPref

@SuppressLint("SetJavaScriptEnabled")
class SearchActivity : AppCompatActivity() {

    private var histories: List<String>? = null
    private var historyPref: HistoryPref? = null
    private var recyclerView: RecyclerView? = null
    private var viewAdapter: RecyclerView.Adapter<*>? = null
    private var viewManager: RecyclerView.LayoutManager? = null
    private var inputEdit: EditText? = null
    private var homeImage: ImageView? = null
    private var credentialImage: ImageView? = null
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        inputEdit = findViewById<EditText>(R.id.search_input)
        homeImage = findViewById<ImageView>(R.id.home_image)
        credentialImage = findViewById<ImageView>(R.id.credential_image)
        webView = findViewById(R.id.search_web_view)
        recyclerView = findViewById<RecyclerView>(R.id.search_recycle)
        historyPref = HistoryPref(this)

        initHeader()
        initHistoryRecycle()
        initWebView()

    }

    private fun initHeader() {
        inputEdit?.setOnEditorActionListener { _, actionId, _ ->
            return@setOnEditorActionListener when (actionId) {
                EditorInfo.IME_ACTION_GO -> {
                    handleWebUrl(inputEdit?.text.toString())
                    true
                }
                else -> false
            }
        }
        homeImage?.setOnClickListener{
            startActivity(Intent(this@SearchActivity, MainActivity::class.java))
            finish()
        }
        credentialImage?.setOnClickListener{
            startActivity(Intent(this@SearchActivity, CredentialsActivity::class.java))
            finish()
        }
    }

    private fun handleWebUrl(url: String) {
        recyclerView?.visibility = View.GONE
        webView?.visibility = View.VISIBLE
        webView?.loadUrl(url)
        historyPref?.putHistoryUrl(url)
        val imm = this.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(inputEdit?.windowToken, 0)
    }

    private fun initHistoryRecycle() {
        histories = historyPref!!.getHistoryUrls()
        if (histories != null && histories!!.isNotEmpty()) {
            viewManager = LinearLayoutManager(this)
            viewAdapter = HistoryAdapter(histories!!, this)
            recyclerView?.apply {
                setHasFixedSize(true)
                layoutManager = viewManager
                adapter = viewAdapter
            }
        }
    }

    private fun initWebView() {
        webView?.settings?.javaScriptEnabled = true
        webView?.settings?.pluginState = WebSettings.PluginState.ON
        webView?.settings?.domStorageEnabled = true
        webView?.settings?.useWideViewPort = false
        webView?.settings?.setSupportZoom(false)
        webView?.settings?.allowFileAccess = true
        webView?.settings?.loadsImagesAutomatically = true
        webView?.settings?.builtInZoomControls = false
        webView?.webViewClient = SearchWebViewClient()
    }

    private class SearchWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
           return false
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView?.canGoBack()!!) {
            webView?.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    class HistoryAdapter(private val histories: List<String>, private val context: SearchActivity) :
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
            viewHolder.itemView.setOnClickListener{
                println(histories[position])
                context.handleWebUrl(histories[position])
            }
        }

        override fun getItemCount() = histories.size

    }

}
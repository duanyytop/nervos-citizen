package org.nervos.gw

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.nervos.gw.db.Identity
import org.nervos.gw.db.IdentityDatabase
import org.nervos.gw.passport.AddPassportActivity
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class CredentialsActivity : AppCompatActivity() {

    private var recyclerView: RecyclerView? = null
    private var viewAdapter: RecyclerView.Adapter<*>? = null
    private var viewManager: RecyclerView.LayoutManager? = null
    private var identities: List<Identity>? = null

    private val threadExecutor: Executor = ThreadPoolExecutor(
        2, 4, 30,
        TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>(4)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_credentials)
        initView()
    }

    private fun initView() {
        val toolbar = findViewById<Toolbar>(R.id.credentials_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener{
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        findViewById<Button>(R.id.add_credentials).setOnClickListener(View.OnClickListener {
            startActivity(Intent(this, AddPassportActivity::class.java))
        })

        threadExecutor.execute {
            identities = IdentityDatabase.instance(applicationContext).identityDao().getAll()
            runOnUiThread {
                viewManager = LinearLayoutManager(this)
                viewAdapter = IdentityAdapter(identities!!)
                recyclerView = findViewById<RecyclerView>(R.id.credentials_recycle).apply {
                    setHasFixedSize(true)
                    layoutManager = viewManager
                    adapter = viewAdapter
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        threadExecutor.execute {
            identities = IdentityDatabase.instance(applicationContext).identityDao().getAll()
            runOnUiThread {
                viewAdapter?.notifyDataSetChanged()
            }
        }
    }


    class IdentityAdapter(private val identities: List<Identity>) :
        RecyclerView.Adapter<IdentityAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            var passportNumber: TextView? = null
            var passportName: TextView? = null
            var passportIssuer: TextView? = null

            init {
                passportNumber = view.findViewById(R.id.item_passport_number)
                passportName = view.findViewById(R.id.item_passport_name)
                passportIssuer = view.findViewById(R.id.item_passport_issuer)
            }
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.item_credential, viewGroup, false)

            return ViewHolder(view)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            val identity = identities[position]
            viewHolder.passportNumber?.text = identity.passportNumber
            viewHolder.passportName?.text = identity.name
            viewHolder.passportIssuer?.text = identity.country
        }

        override fun getItemCount() = identities.size

    }


}
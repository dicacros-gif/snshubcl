package com.snshubcl.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

/**
 * 대시보드 화면.
 *
 * 5개 SNS 카드를 만들고, 카드마다
 *  - 콘텐츠 언어(한국어/English) 토글 — SNS별로 따로 저장
 *  - '웹뷰로 열기'  : 앱 내 WebView(WebActivity)로 해당 플랫폼을 연다
 *  - '앱으로 열기'  : 설치된 네이티브 앱을 띄운다(없으면 스토어)
 * 를 제공한다.
 *
 * 자동화 없음: 친구추가/팔로우/연결 등은 열린 화면에서 사용자가 직접 누른다.
 */
class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("snshub_prefs", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val list = findViewById<LinearLayout>(R.id.list)
        val inflater = LayoutInflater.from(this)

        for (net in Sns.ALL) {
            val card = inflater.inflate(R.layout.item_sns, list, false)
            val accent = Color.parseColor(net.accent)

            card.findViewById<View>(R.id.accentStrip).setBackgroundColor(accent)
            card.findViewById<TextView>(R.id.snsName).apply {
                text = net.name
                setTextColor(accent)
            }

            val toggle = card.findViewById<MaterialButtonToggleGroup>(R.id.langToggle)
            val btnKo = card.findViewById<MaterialButton>(R.id.btnLangKo)
            val btnEn = card.findViewById<MaterialButton>(R.id.btnLangEn)
            val saved = prefs.getString("lang_${net.id}", "ko")
            toggle.check(if (saved == "en") btnEn.id else btnKo.id)
            toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                val lang = if (checkedId == btnEn.id) "en" else "ko"
                prefs.edit().putString("lang_${net.id}", lang).apply()
            }

            card.findViewById<MaterialButton>(R.id.btnWeb).setOnClickListener {
                val lang = if (toggle.checkedButtonId == btnEn.id) "en" else "ko"
                startActivity(Intent(this, WebActivity::class.java).apply {
                    putExtra("sns", net.id)
                    putExtra("lang", lang)
                })
            }
            card.findViewById<MaterialButton>(R.id.btnApp).setOnClickListener {
                openNativeApp(net.pkg)
            }

            list.addView(card)
        }
    }

    /** 설치된 네이티브 앱을 실행. 없으면 Play 스토어(있으면 스토어 앱, 없으면 웹)로 보낸다. */
    private fun openNativeApp(pkg: String) {
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) {
            startActivity(launch)
            return
        }
        Toast.makeText(this, R.string.app_not_installed, Toast.LENGTH_SHORT).show()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Sns.storeUrl(pkg))))
        }
    }
}

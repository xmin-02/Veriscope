package com.example.veriscope.utils

import android.os.AsyncTask
import java.util.*
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class EmailSender {
    
    companion object {
        // Gmail SMTP 설정
        private const val SMTP_HOST = "smtp.gmail.com"
        private const val SMTP_PORT = "587"
        private const val EMAIL = "smartit.ngms@gmail.com"
        private const val PASSWORD = "lnpd wvsj qzjh wfgm" // Gmail 앱 비밀번호
    }
    
    fun sendEmail(
        toEmail: String,
        subject: String,
        body: String,
        callback: (Boolean, String?) -> Unit
    ) {
        EmailTask(toEmail, subject, body, callback).execute()
    }
    
    private class EmailTask(
        private val toEmail: String,
        private val subject: String,
        private val body: String,
        private val callback: (Boolean, String?) -> Unit
    ) : AsyncTask<Void, Void, Boolean>() {
        
        private var errorMessage: String? = null
        
        override fun doInBackground(vararg params: Void?): Boolean {
            return try {
                val properties = Properties().apply {
                    put("mail.smtp.host", SMTP_HOST)
                    put("mail.smtp.port", SMTP_PORT)
                    put("mail.smtp.auth", "true")
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.starttls.required", "true")
                    put("mail.smtp.ssl.enable", "true")
                    put("mail.smtp.ssl.protocols", "TLSv1.2")
                    put("mail.smtp.socketFactory.port", SMTP_PORT)
                    put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                    put("mail.smtp.socketFactory.fallback", "false")
                    put("mail.smtp.ssl.checkserveridentity", "true")
                    put("mail.smtp.ssl.trust", "*")
                    put("mail.debug", "true")
                }
                
                val session = Session.getInstance(properties, object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(EMAIL, PASSWORD)
                    }
                })
                
                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(EMAIL))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
                    setSubject(subject, "UTF-8")
                    setText(body, "UTF-8")
                }
                
                Transport.send(message)
                true
            } catch (e: Exception) {
                errorMessage = e.message
                android.util.Log.e("EmailSender", "이메일 발송 오류", e)
                false
            }
        }
        
        override fun onPostExecute(result: Boolean) {
            callback(result, errorMessage)
        }
    }
}
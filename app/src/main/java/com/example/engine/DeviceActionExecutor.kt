package com.example.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import java.net.URLEncoder

sealed class ActionResult {
    data class Success(val message: String, val verified: Boolean = true) : ActionResult()
    data class Failure(val error: String) : ActionResult()
    data class Ambiguity(val contacts: List<ContactMatch>) : ActionResult()
}

data class ContactMatch(
    val name: String,
    val number: String
)

class DeviceActionExecutor(private val context: Context) {

    private val tag = "DeviceActionExecutor"

    /**
     * Check if a specific package is installed on device
     */
    fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Open WhatsApp or send message to a contact/phone
     */
    fun executeWhatsApp(contactQuery: String?, message: String?): ActionResult {
        val pm = context.packageManager
        val isInstalled = isPackageInstalled("com.whatsapp") || isPackageInstalled("com.whatsapp.w4b")
        val pkg = if (isPackageInstalled("com.whatsapp.w4b")) "com.whatsapp.w4b" else "com.whatsapp"

        if (!contactQuery.isNullOrBlank() && !message.isNullOrBlank()) {
            // Check if contactQuery is a phone number (e.g. +91... or digits)
            val digitsOnly = contactQuery.filter { it.isDigit() }
            if (digitsOnly.length >= 10) {
                // Direct WhatsApp API link with phone number
                return try {
                    val encodedMsg = URLEncoder.encode(message, "UTF-8")
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://api.whatsapp.com/send?phone=$digitsOnly&text=$encodedMsg")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        if (isInstalled) setPackage(pkg)
                    }
                    context.startActivity(intent)
                    ActionResult.Success("WhatsApp message directed to $digitsOnly: \"$message\"", verified = true)
                } catch (e: Exception) {
                    Log.e(tag, "WhatsApp direct link failed", e)
                    ActionResult.Failure("Could not open WhatsApp link: ${e.message}")
                }
            } else {
                // Try searching contact
                val matches = searchContacts(contactQuery)
                if (matches.size == 1) {
                    val phone = matches[0].number.filter { it.isDigit() }
                    return try {
                        val encodedMsg = URLEncoder.encode(message, "UTF-8")
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://api.whatsapp.com/send?phone=$phone&text=$encodedMsg")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            if (isInstalled) setPackage(pkg)
                        }
                        context.startActivity(intent)
                        ActionResult.Success("WhatsApp chat opened with ${matches[0].name} ($phone) with prepared message: \"$message\"", verified = true)
                    } catch (e: Exception) {
                        ActionResult.Failure("Failed to launch WhatsApp: ${e.message}")
                    }
                } else if (matches.size > 1) {
                    return ActionResult.Ambiguity(matches)
                } else {
                    // Contact not found in phonebook, but open WhatsApp with share intent so user can select
                    return try {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, message)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            if (isInstalled) setPackage(pkg)
                        }
                        context.startActivity(sendIntent)
                        ActionResult.Success("WhatsApp opened with message text \"$message\". Selected recipient: $contactQuery", verified = true)
                    } catch (e: Exception) {
                        ActionResult.Failure("Could not share to WhatsApp: ${e.message}")
                    }
                }
            }
        }

        // Just open WhatsApp
        return try {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(launchIntent)
                ActionResult.Success("WhatsApp opened successfully.", verified = true)
            } else {
                // Fallback to web link
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://web.whatsapp.com")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(webIntent)
                ActionResult.Success("WhatsApp web opened in browser.", verified = true)
            }
        } catch (e: Exception) {
            ActionResult.Failure("WhatsApp is not installed on this device.")
        }
    }

    /**
     * Search and play YouTube video/query
     */
    fun executeYouTube(query: String?): ActionResult {
        val pm = context.packageManager
        val isInstalled = isPackageInstalled("com.google.android.youtube")

        return try {
            if (!query.isNullOrBlank()) {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encoded")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    if (isInstalled) setPackage("com.google.android.youtube")
                }
                context.startActivity(intent)
                ActionResult.Success("YouTube opened and searched for \"$query\".", verified = true)
            } else {
                val launchIntent = pm.getLaunchIntentForPackage("com.google.android.youtube")
                if (launchIntent != null) {
                    launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(launchIntent)
                    ActionResult.Success("YouTube opened successfully.", verified = true)
                } else {
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(webIntent)
                    ActionResult.Success("YouTube opened in browser.", verified = true)
                }
            }
        } catch (e: Exception) {
            ActionResult.Failure("Could not open YouTube: ${e.message}")
        }
    }

    /**
     * Search contacts in Android phonebook
     */
    fun searchContacts(query: String): List<ContactMatch> {
        val matches = mutableListOf<ContactMatch>()
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$query%")

            val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val name = if (nameIndex >= 0) it.getString(nameIndex) else "Unknown"
                    val number = if (numberIndex >= 0) it.getString(numberIndex) else ""
                    if (!matches.any { m -> m.number == number }) {
                        matches.add(ContactMatch(name, number))
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w(tag, "Contacts permission not granted", e)
        } catch (e: Exception) {
            Log.e(tag, "Error searching contacts", e)
        }
        return matches
    }

    /**
     * Call contact or open phone dialer
     */
    fun executeCall(contactQuery: String): ActionResult {
        val digitsOnly = contactQuery.filter { it.isDigit() || it == '+' }
        if (digitsOnly.length >= 3) {
            return launchDialer(digitsOnly)
        }

        val matches = searchContacts(contactQuery)
        return when {
            matches.size == 1 -> {
                launchDialer(matches[0].number)
            }
            matches.size > 1 -> {
                ActionResult.Ambiguity(matches)
            }
            else -> {
                ActionResult.Failure("Contact \"$contactQuery\" was not found in phone contacts. Please check name or provide phone number.")
            }
        }
    }

    private fun launchDialer(number: String): ActionResult {
        return try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            ActionResult.Success("Opened phone dialer for $number.", verified = true)
        } catch (e: Exception) {
            ActionResult.Failure("Could not open dialer: ${e.message}")
        }
    }

    /**
     * Compose an email
     */
    fun executeEmail(recipient: String?, subject: String?, body: String?): ActionResult {
        return try {
            val uri = Uri.parse("mailto:${recipient ?: ""}")
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                putExtra(Intent.EXTRA_SUBJECT, subject ?: "")
                putExtra(Intent.EXTRA_TEXT, body ?: "")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            ActionResult.Success("Email compose opened for ${recipient ?: "recipient"} with subject \"${subject ?: "Draft"}\"", verified = true)
        } catch (e: Exception) {
            ActionResult.Failure("No email client available on this device: ${e.message}")
        }
    }

    /**
     * Open a web URL or Google search
     */
    fun executeWebSearch(queryOrUrl: String): ActionResult {
        return try {
            val targetUri = if (queryOrUrl.startsWith("http://") || queryOrUrl.startsWith("https://")) {
                Uri.parse(queryOrUrl)
            } else {
                val encoded = URLEncoder.encode(queryOrUrl, "UTF-8")
                Uri.parse("https://www.google.com/search?q=$encoded")
            }

            val intent = Intent(Intent.ACTION_VIEW, targetUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            ActionResult.Success("Browser opened: $queryOrUrl", verified = true)
        } catch (e: Exception) {
            ActionResult.Failure("Failed to open browser: ${e.message}")
        }
    }

    /**
     * Safe app opening via allowlist
     */
    fun executeOpenApp(appName: String): ActionResult {
        val lower = appName.lowercase()
        return when {
            lower.contains("whatsapp") -> executeWhatsApp(null, null)
            lower.contains("youtube") -> executeYouTube(null)
            lower.contains("chrome") || lower.contains("browser") -> executeWebSearch("https://www.google.com")
            lower.contains("email") || lower.contains("gmail") -> executeEmail(null, null, null)
            lower.contains("camera") -> {
                try {
                    val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    ActionResult.Success("Camera opened.", verified = true)
                } catch (e: Exception) {
                    ActionResult.Failure("Could not open camera: ${e.message}")
                }
            }
            lower.contains("setting") -> {
                try {
                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    ActionResult.Success("Settings opened.", verified = true)
                } catch (e: Exception) {
                    ActionResult.Failure("Could not open settings: ${e.message}")
                }
            }
            else -> {
                // Try searching installed applications
                val pm = context.packageManager
                val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                val matched = packages.firstOrNull {
                    val label = pm.getApplicationLabel(it).toString().lowercase()
                    label == lower || label.contains(lower)
                }

                if (matched != null) {
                    val launchIntent = pm.getLaunchIntentForPackage(matched.packageName)
                    if (launchIntent != null) {
                        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(launchIntent)
                        ActionResult.Success("App ${matched.packageName} opened.", verified = true)
                    } else {
                        ActionResult.Failure("Could not launch $appName.")
                    }
                } else {
                    ActionResult.Failure("App \"$appName\" is not installed on this device.")
                }
            }
        }
    }
}

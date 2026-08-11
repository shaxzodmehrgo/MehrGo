package uz.teamwork.mehrgodriver.presentation.main.ui.verification

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import id.zelory.compressor.Compressor
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.Constants
import uz.teamwork.mehrgodriver.common.Constants.APPLICATION_ID
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.common.fitSystemBars
import uz.teamwork.mehrgodriver.common.setDebouncedClickListener
import uz.teamwork.mehrgodriver.common.showToast
import uz.teamwork.mehrgodriver.databinding.AdapterVerificationCheckBinding
import uz.teamwork.mehrgodriver.databinding.DialogBshChooseCameraGalleryBinding
import uz.teamwork.mehrgodriver.databinding.FragmentVerificationBinding
import uz.teamwork.mehrgodriver.databinding.ItemVerificationDoneBinding
import uz.teamwork.mehrgodriver.domain.model.VerificationCheck
import uz.teamwork.mehrgodriver.domain.model.VerificationStatus
import java.io.File
import java.io.FileOutputStream

private const val KEY_LICENSE_PHOTO_PATH = "verification_license_photo_path"

@AndroidEntryPoint
class VerificationFragment : Fragment() {

    private var _binding: FragmentVerificationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: VerificationViewModel by viewModels()

    private var loadJob: Job? = null
    private var licensePhotoPath: String? = null

    // Moderator approved/rejected a document while this screen is open → re-fetch.
    private val verificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            load()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVerificationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Restore the pending camera-capture path if the process was killed while the camera was open.
        if (licensePhotoPath == null) {
            licensePhotoPath = savedInstanceState?.getString(KEY_LICENSE_PHOTO_PATH)
        }
        binding.mcvBack.setDebouncedClickListener { findNavController().navigateUp() }
        // Single end-of-screen skip (replaces the old per-card skip): dismisses the verification
        // screen. It is only ever shown when nothing mandatory is outstanding (see render()), so
        // closing here can never let a driver bypass a required check.
        binding.tvSkipAll.setDebouncedClickListener { findNavController().navigateUp() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_LICENSE_PHOTO_PATH, licensePhotoPath)
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            verificationReceiver,
            IntentFilter(Constants.ACTION_VERIFICATION_STATUS_CHANGED)
        )
        // Re-fetch on every entry so a moderator decision made while away is reflected.
        load()
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(verificationReceiver)
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getStatus().collect { res ->
                when (res) {
                    is Resource.Loading -> showLoading(true)
                    is Resource.Success -> {
                        showLoading(false)
                        render(res.data?.data)
                    }

                    is Resource.Error -> {
                        showLoading(false)
                        showToast(res.message ?: getString(R.string.error))
                    }
                }
            }
        }
    }

    private fun render(status: VerificationStatus?) {
        val visible = status?.visibleChecks ?: emptyList()

        // A pending (under-review) blocker still gates online, but shows as an amber "under review"
        // row in Done — not the red Required section — so the driver isn't told to re-upload.
        val required = visible.filter { it.isActionableBlocking }
        val optional = visible.filter { it.isSuggestion && !it.isPending }
        val done = visible.filter { it.ok || it.isPending }

        fillCardSection(binding.tvRequiredHeader, binding.llRequired, required, isRequired = true)
        fillCardSection(binding.tvOptionalHeader, binding.llOptional, optional, isRequired = false)
        fillDoneSection(done)

        // The skip button only appears when there is something optional to skip AND no mandatory
        // check is outstanding. `hasBlocking` covers any failing required / non-skippable check,
        // including ones already under review — so a blocked driver never sees a way to dismiss.
        val hasBlocking = status?.hasBlocking == true
        binding.tvSkipAll.isVisible = optional.isNotEmpty() && !hasBlocking

        binding.tvEmpty.isVisible = visible.isEmpty()
        updateProgress(status, visible)
    }

    private fun fillCardSection(
        header: View,
        container: LinearLayout,
        checks: List<VerificationCheck>,
        isRequired: Boolean
    ) {
        header.isVisible = checks.isNotEmpty()
        container.isVisible = checks.isNotEmpty()
        container.removeAllViews()
        checks.forEach { check ->
            val item = AdapterVerificationCheckBinding.inflate(layoutInflater, container, false)
            bindFullCard(item, check, isRequired)
            container.addView(item.root)
        }
    }

    private fun bindFullCard(
        item: AdapterVerificationCheckBinding,
        check: VerificationCheck,
        isRequired: Boolean
    ) {
        val ctx = item.root.context

        item.tvTitle.text = check.title.orEmpty()
        val message = check.message.orEmpty()
        item.tvMessage.isVisible = message.isNotBlank()
        item.tvMessage.text = message

        val colorRes = if (isRequired) R.color.red else R.color.app_color
        val badgeRes = if (isRequired) R.string.verification_status_required
        else R.string.verification_status_optional
        val color = ContextCompat.getColor(ctx, colorRes)
        val soft = softColor(color)

        item.ivStatusIcon.setImageResource(R.drawable.ph_warning)
        item.ivStatusIcon.setColorFilter(color)
        item.cvStatusIcon.setCardBackgroundColor(soft)
        item.tvBadge.setText(badgeRes)
        item.tvBadge.setTextColor(color)
        item.tvBadge.backgroundTintList = ColorStateList.valueOf(soft)

        // Required checks sit on a soft-red card so the blocker is unmistakable;
        // optional checks keep the default white card (set in XML).
        if (isRequired) {
            val redSoft = ContextCompat.getColor(ctx, R.color.red_soft)
            item.root.setCardBackgroundColor(redSoft)
            item.root.setStrokeColor(redSoft)
        }

        item.tvVideo.isVisible = check.hasVideo
        item.tvVideo.setDebouncedClickListener { openUrl(check.videoUrl.orEmpty()) }

        item.tvAction.isVisible = check.canUploadLicense
        item.tvAction.setDebouncedClickListener { showPhotoChooser() }
    }

    private fun fillDoneSection(done: List<VerificationCheck>) {
        binding.tvDoneHeader.isVisible = done.isNotEmpty()
        binding.cvDone.isVisible = done.isNotEmpty()
        binding.llDone.removeAllViews()
        done.forEachIndexed { index, check ->
            val row = ItemVerificationDoneBinding.inflate(layoutInflater, binding.llDone, false)
            bindDoneRow(row, check, isFirst = index == 0)
            binding.llDone.addView(row.root)
        }
    }

    private fun bindDoneRow(
        row: ItemVerificationDoneBinding,
        check: VerificationCheck,
        isFirst: Boolean
    ) {
        val ctx = row.root.context
        row.divider.isVisible = !isFirst
        row.tvTitle.text = check.title.orEmpty()

        if (check.ok) {
            val green = ContextCompat.getColor(ctx, R.color.green)
            row.ivIcon.setImageResource(R.drawable.ph_check_circle)
            row.ivIcon.setColorFilter(green)
            row.tvStatus.setText(R.string.verification_status_ok)
            row.tvStatus.setTextColor(green)
        } else {
            val amber = ContextCompat.getColor(ctx, R.color.app_color)
            row.ivIcon.setImageResource(R.drawable.ph_clock)
            row.ivIcon.setColorFilter(amber)
            row.tvStatus.setText(R.string.verification_status_pending)
            row.tvStatus.setTextColor(amber)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateProgress(status: VerificationStatus?, visible: List<VerificationCheck>) {
        val total = visible.size
        binding.cvProgress.isVisible = total > 0
        if (total == 0) return

        val doneCount = visible.count { it.ok }
        binding.vProgressFill.updateLayoutParams<LinearLayout.LayoutParams> {
            weight = doneCount.toFloat()
        }
        binding.vProgressRest.updateLayoutParams<LinearLayout.LayoutParams> {
            weight = (total - doneCount).toFloat()
        }
        binding.tvProgressCount.text = "$doneCount/$total"

        val blockingCount = visible.count { it.isBlocking }
        when {
            blockingCount > 0 ->
                binding.tvProgressTitle.text =
                    getString(R.string.verification_progress_required, blockingCount)

            status?.allPassed == true ->
                binding.tvProgressTitle.setText(R.string.verification_all_passed)

            else ->
                binding.tvProgressTitle.setText(R.string.verification_progress_ready)
        }
    }

    /** Same ~15% alpha wash the design system uses for status badges/chips. */
    private fun softColor(color: Int): Int = (color and 0x00FFFFFF) or (0x26 shl 24)

    private fun showLoading(loading: Boolean) {
        binding.progressBar.isVisible = loading
        binding.content.isVisible = !loading
    }

    private fun openUrl(url: String) {
        if (url.isBlank()) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            showToast(getString(R.string.error))
        }
    }

    // region license photo upload (mirrors CompleteDriverInfoFragment)
    private fun showPhotoChooser() {
        val sheet = BottomSheetDialog(requireContext()).fitSystemBars()
        val sheetBinding =
            DialogBshChooseCameraGalleryBinding.inflate(LayoutInflater.from(requireContext()))
        sheet.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        sheet.setContentView(sheetBinding.root)

        sheetBinding.tvCamera.setDebouncedClickListener {
            takeFromCamera()
            sheet.dismiss()
        }
        sheetBinding.tvGallery.setDebouncedClickListener {
            galleryLauncher.launch("image/*")
            sheet.dismiss()
        }
        sheet.show()
    }

    private fun takeFromCamera() {
        val file = try {
            createImageFile()
        } catch (e: Exception) {
            null
        }
        val uri = file?.let { FileProvider.getUriForFile(requireContext(), APPLICATION_ID, it) }
        if (uri != null) cameraLauncher.launch(uri)
    }

    private fun createImageFile(): File {
        val dir = requireActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("${System.currentTimeMillis()}", ".jpg", dir).apply {
            licensePhotoPath = absolutePath
        }
    }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
            if (isSuccess && licensePhotoPath != null) {
                uploadLicense()
            } else {
                licensePhotoPath = null
            }
        }

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                val input = requireActivity().contentResolver.openInputStream(uri)
                val file = File(requireActivity().filesDir, "${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { output -> input?.copyTo(output) }
                input?.close()
                licensePhotoPath = file.absolutePath
                uploadLicense()
            } catch (e: Exception) {
                // Content-resolver failure (revoked grant, cloud provider, unreadable Uri) — don't
                // crash the result callback; let the driver retry.
                showToast(getString(R.string.error))
            }
        }

    private fun uploadLicense() {
        val path = licensePhotoPath ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val original = File(path)
                val compressed = Compressor.compress(requireContext(), original)
                val body = RequestBody.create("multipart/form-data".toMediaTypeOrNull(), compressed)
                val part = MultipartBody.Part.createFormData("license", original.name, body)

                viewModel.uploadLicense(part).collect { res ->
                    when (res) {
                        is Resource.Loading -> showLoading(true)
                        is Resource.Success -> {
                            showLoading(false)
                            licensePhotoPath = null
                            showToast(getString(R.string.verification_upload_success))
                            load()
                        }

                        is Resource.Error -> {
                            showLoading(false)
                            licensePhotoPath = null
                            showToast(res.message ?: getString(R.string.error))
                            // 409 "already pending" / any server error: re-fetch so the row reflects
                            // the true server state (e.g. it actually became pending) instead of
                            // staying stuck on the "upload photo" CTA.
                            load()
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Compress / file / multipart failure (corrupt, 0-byte, HEIC, revoked Uri…) — surface
                // an error instead of crashing on Dispatchers.Main.
                if (_binding != null) {
                    showLoading(false)
                    licensePhotoPath = null
                    showToast(getString(R.string.error))
                }
            }
        }
    }
    // endregion

    override fun onDestroyView() {
        super.onDestroyView()
        loadJob?.cancel()
        loadJob = null
        _binding = null
    }
}

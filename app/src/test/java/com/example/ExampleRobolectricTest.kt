package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.DocType
import com.example.data.repository.DocRepository
import com.example.ui.viewmodel.DocIntelViewModel
import com.example.ui.viewmodel.EngineStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Cognitive Doc AI", appName)
  }

  @Test
  fun `test viewModel and repository integration completes successfully`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = DocRepository(context)
    val viewModel = DocIntelViewModel(repository)
    
    // Check that we can fetch the legal preset document immediately
    val legalPreset = repository.getPresetDocument(DocType.LEGAL)
    assert(legalPreset.title.contains("Apex"))
    
    // Verify that we can load preset document (which kicks off simulation)
    viewModel.loadPresetDocument(DocType.LEGAL)
    assert(viewModel.engineStatus.value is EngineStatus.Simulating)
  }

  @Test
  fun `test mainActivity launches cleanly`() {
    val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
    assert(controller.get() != null)
  }
}

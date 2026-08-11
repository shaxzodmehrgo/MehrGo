package uz.teamwork.mehrgodriver.presentation.main.ui.instuction

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.Instruction
import uz.teamwork.mehrgodriver.domain.model.base.BaseResponse
import uz.teamwork.mehrgodriver.domain.use_case.main.InstructionUC
import javax.inject.Inject

@HiltViewModel
class InstructionViewModel @Inject constructor(private val instructionUC: InstructionUC) :
    ViewModel() {
    fun getInstruction(): Flow<Resource<BaseResponse<List<Instruction>>>> {
        return instructionUC.invoke()
    }
}
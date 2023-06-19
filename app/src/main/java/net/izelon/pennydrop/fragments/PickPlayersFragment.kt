package net.izelon.pennydrop.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import net.izelon.pennydrop.R
import net.izelon.pennydrop.databinding.FragmentPickPlayersBinding
import net.izelon.pennydrop.viewmodels.GameViewModel
import net.izelon.pennydrop.viewmodels.PickPlayersViewModel


class PickPlayersFragment : Fragment() {
    private val pickPlayersViewModel by activityViewModels<PickPlayersViewModel>()
    private val gameViewModel by activityViewModels<GameViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentPickPlayersBinding.inflate(inflater, container, false).apply {
            this.vm = pickPlayersViewModel

            this.buttonPlayGame.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    gameViewModel.startGame(pickPlayersViewModel.players.value?.filter { newPlayer ->
                        newPlayer.isIncluded.get()
                    }?.map { newPlayer ->
                        newPlayer.toPlayer()
                    } ?: emptyList())

                    findNavController().navigate(R.id.gameFragment)
                }
            }

        }

        return binding.root
    }

}
package com.gg_tech_bharat.gdialer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class FavoritesFragment extends Fragment {

    private RecyclerView rvFavorites;
    private FavoriteAdapter adapter;
    private AppDatabase database;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_favorites, container, false);

        database = AppDatabase.getDatabase(requireContext());

        rvFavorites = view.findViewById(R.id.rvFavorites);
        // Setup Grid with 3 columns for OneUI favorite card design
        rvFavorites.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        adapter = new FavoriteAdapter(requireContext());
        rvFavorites.setAdapter(adapter);

        // Load favorites
        database.contactDao().getFavoriteContacts().observe(getViewLifecycleOwner(), favorites -> {
            if (favorites != null) {
                adapter.setFavorites(favorites);
            }
        });

        return view;
    }
}

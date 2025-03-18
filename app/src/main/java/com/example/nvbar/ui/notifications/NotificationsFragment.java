package com.example.nvbar.ui.notifications;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.nvbar.DetectNcnn;
import com.example.nvbar.MainActivity;
import com.example.nvbar.R;
import com.example.nvbar.databinding.FragmentNotificationsBinding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NotificationsFragment extends Fragment {
    static {
        System.loadLibrary("detect");
    }
    private DetectNcnn detectNcnn = new DetectNcnn();
    private int modelId = 0;
    private static final List<Integer> SCALES = new ArrayList<>(Arrays.asList(4, 4, 4, 4));;  //为以后添加模型做准备
    private Bitmap yourSelectedImage = null;
    private Spinner ganSpinner;
    private FragmentNotificationsBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        NotificationsViewModel notificationsViewModel =
                new ViewModelProvider(this).get(NotificationsViewModel.class);

        binding = FragmentNotificationsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

//        final TextView textView = binding.textNotifications;
//        notificationsViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);

        ganSpinner = root.findViewById(R.id.gan_spinner);
        ganSpinner.setSelection(modelId);

        Button ganButton = (Button) root.findViewById(R.id.btn_gan_process);
        Button loadButton = (Button) root.findViewById(R.id.btn_gan_load);

        loadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                modelId = ganSpinner.getSelectedItemPosition();
                boolean ret = detectNcnn.GanInit(requireActivity().getApplicationContext().getAssets(), modelId);
                Toast.makeText(requireActivity().getApplicationContext(), "模型加载成功！", Toast.LENGTH_SHORT).show();
            }
        });

        ganButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                yourSelectedImage = ((MainActivity) getActivity()).doodleView.getBitmap();
                ganInfer();
            }
        });

        ((MainActivity) getActivity()).setOnDataChangeListener(new MainActivity.OnDataChangeListener() {
            @Override
            public void onDataChange(Bitmap data) {
                yourSelectedImage = data;
            }
        });

        return root;
    }
    private void ganInfer(){
        if (yourSelectedImage == null)
            return;
        Bitmap saveBitmap = Bitmap.createBitmap(
                yourSelectedImage.getWidth() * SCALES.get(modelId), yourSelectedImage.getHeight() * SCALES.get(modelId),
                Bitmap.Config.ARGB_8888
        );
        detectNcnn.GanInfer(yourSelectedImage, saveBitmap);
        // Bitmap bitmap_out = filmEffect(yourSelectedImage);

        // todo UI更新代码
        // todo UI更新代码
        ((MainActivity) getActivity()).setReceive(saveBitmap);  // 将生成的图片发送到mainact中，以便后续保存图片
        ((MainActivity) getActivity()).mImg.setImageBitmap(saveBitmap);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
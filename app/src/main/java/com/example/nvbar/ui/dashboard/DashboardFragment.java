package com.example.nvbar.ui.dashboard;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.nvbar.DetectNcnn;
import com.example.nvbar.MainActivity;
import com.example.nvbar.R;
import com.example.nvbar.databinding.FragmentDashboardBinding;

import java.util.Objects;

public class DashboardFragment extends Fragment {
    static {
        System.loadLibrary("detect");
    }
    private DetectNcnn detectNcnn = new DetectNcnn();
    private int modelId = 0;
    private Bitmap yourSelectedImage = null;
    private FragmentDashboardBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        DashboardViewModel dashboardViewModel =
                new ViewModelProvider(this).get(DashboardViewModel.class);

        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

//        final TextView textView = binding.textDashboard;
//        dashboardViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);
        //根据ID找到RadioGroup实例
        RadioGroup group = (RadioGroup) root.findViewById(R.id.u2netRadioGroup);
        Button u2netButton = (Button) root.findViewById(R.id.btn_u2net_process);
        Button loadButton = (Button) root.findViewById(R.id.btn_u2net_load);

        loadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                detectNcnn.U2netInit(requireActivity().getApplicationContext().getAssets(), modelId);
                Toast.makeText(requireActivity().getApplicationContext(), "模型加载成功！", Toast.LENGTH_SHORT).show();
            }
        });

        u2netButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
//                if (getArguments() == null) {
//                    return;
//                }
//                else
//                    {
//                    // ----------------在这里---------------
//                    // 从参数中获取data1的值，这里"data""int_data"是键名
//                    data1 = getArguments().getString("data");
//                    yourSelectedImage = getArguments().getParcelable("bitmap");  // 从mainactivity获取bitmap对象
//                }
                yourSelectedImage = ((MainActivity) getActivity()).doodleView.getBitmap();
                u2netInfer();
            }
        });


        ((MainActivity) getActivity()).setOnDataChangeListener(new MainActivity.OnDataChangeListener() {
            @Override
            public void onDataChange(Bitmap data) {
                yourSelectedImage = data;
            }
        });

        //绑定一个匿名监听器
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup arg0, int arg1) {
                //获取变更后的选中项的ID
                int radioButtonId = arg0.getCheckedRadioButtonId();
                //根据ID获取RadioButton的实例
                RadioButton rb = (RadioButton) root.findViewById(radioButtonId);
                //更新文本内容，以符合选中项
                // tv.setText("您的性别是：" + rb.getText());
                int count = arg0.getChildCount();
                for (int i = 0; i < count; i++) {

                    if (arg0.getChildAt(i).getId() == arg1) {
                        modelId = i;
                        break;
                    }
                }
            }
        });

        return root;
    }
    private void u2netInfer(){
        if (yourSelectedImage == null)
            return;

        Bitmap yourSelectedImageIn = yourSelectedImage.copy(Bitmap.Config.ARGB_8888, true);
        Bitmap saveBitmap = Bitmap.createBitmap(
                yourSelectedImageIn.getWidth(), yourSelectedImageIn.getHeight(),
                Bitmap.Config.ARGB_8888
        );
        detectNcnn.U2netInfer(yourSelectedImageIn, saveBitmap);
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
package com.example.nvbar.ui.home;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.nvbar.ImageUtils;
import com.example.nvbar.MainActivity;
import com.example.nvbar.R;
import com.example.nvbar.databinding.FragmentHomeBinding;

import org.pytorch.IValue;
import org.pytorch.MemoryFormat;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.IOException;
import java.util.List;

import cn.hzw.doodle.core.IDoodleItem;


public class HomeFragment extends Fragment {
    private int modelId;
    private static final float[] IMAGE_MEAN = {0.f, 0.f, 0.f};
    private static final float[] IMAGE_STD = {1.f, 1.f, 1.f};
    private RadioGroup group;
    private Module lamaModel = null;
    private final Process pro = new Process();
    private Bitmap yourSelectedImage = null;
    private FragmentHomeBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        HomeViewModel homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
//        final TextView textView = binding.textHome;
//        homeViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);

        //根据ID找到RadioGroup实例
        group = (RadioGroup) root.findViewById(R.id.lamaRadioGroup);
        Button lamaButton = (Button) root.findViewById(R.id.btn_lama_process);
        Button loadButton = (Button) root.findViewById(R.id.btn_lama_load);

        loadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (lamaModel != null){
                    return;
                }
                String s = null;
                try {
                    s = ImageUtils.assetFilePath(requireActivity().getApplicationContext(),
                            "big-lama.torchscripts");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                // 导入lama的pytorch模型
                lamaModel = Module.load(s);
                Toast.makeText(requireActivity().getApplicationContext(), "模型加载成功！", Toast.LENGTH_SHORT).show();
            }
        });

        lamaButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                // 这是从mainActicity中获取到Imageview上的Bitmap的方案
                // yourSelectedImage = ((BitmapDrawable)((MainActivity) getActivity()).mImg.getDrawable()).getBitmap();
                try {
                    lama();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
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
//        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    private void lama() throws IOException {
        if (yourSelectedImage == null)
            return;

        Bitmap yourSelectedImageIn = yourSelectedImage.copy(Bitmap.Config.ARGB_8888, true);

        yourSelectedImageIn = ImageUtils.checkLamaRatio(yourSelectedImageIn);//将分辨率转为合适值
        int H = yourSelectedImageIn.getHeight();
        int W = yourSelectedImageIn.getWidth();
        // preparing input tensor
        Tensor inTensor = TensorImageUtils.bitmapToFloat32Tensor(yourSelectedImageIn,
                IMAGE_MEAN, IMAGE_STD, MemoryFormat.CHANNELS_LAST);

        Bitmap newBitmap = Bitmap.createBitmap(((MainActivity) getActivity()).doodleView.getBitmap().getWidth(),
        ((MainActivity) getActivity()).doodleView.getBitmap().getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(newBitmap);
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        canvas.drawRect(0f, 0f, ((MainActivity) getActivity()).doodleView.getBitmap().getWidth(),
                ((MainActivity) getActivity()).doodleView.getBitmap().getHeight(), paint);
        List<IDoodleItem> items = ((MainActivity) getActivity()).doodleView.getAllItem();
        for (int i = 0; i < items.size(); i++) {
            items.get(i).draw(canvas);
        }
        Bitmap newBitmapIn = ImageUtils.checkLamaRatio(newBitmap);

        int[] inputMask = ImageUtils.generateMask2(newBitmapIn);

        long[] shape1 = {1, 1, H, W}; // CHW format
        Tensor maskTensor = Tensor.fromBlob(inputMask, shape1);

        // 1x3xHxW tensor, torch.float32
        Tensor outTensor = lamaModel.forward(IValue.from(inTensor), IValue.from(maskTensor)).toTensor();

        long aiTime = System.currentTimeMillis();

        // Tensor outTensor = outputs[0].toTensor();
        float[] output = outTensor.getDataAsFloatArray();  // 1x3
        Bitmap saveBitmap = pro.floatArrayToBitmap(output, W, H);
        saveBitmap = Bitmap.createScaledBitmap(saveBitmap, yourSelectedImage.getWidth(), yourSelectedImage.getHeight(), true);

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
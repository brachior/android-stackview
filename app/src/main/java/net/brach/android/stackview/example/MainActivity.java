package net.brach.android.stackview.example;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;

import net.brach.android.stackview.StackView;

import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        StackView stack = (StackView) findViewById(R.id.stack);
        stack.setAdapter(new StackAdapter(
                R.drawable.cat_1,
                R.drawable.cat_2,
                R.drawable.cat_3,
                R.drawable.cat_4
        ));
    }

    private class StackAdapter extends StackView.Adapter<Integer> {
        private final ArrayList<Integer> pictures;

        StackAdapter(Integer... pictures) {
            this.pictures = new ArrayList<>(pictures.length);
            this.pictures.addAll(Arrays.asList(pictures));
        }

        @Override
        public Integer get(Position position) {
            if (position.value + 1 > pictures.size()) {
                return null;
            }
            return pictures.get(position.value);
        }

        @Override
        public View createDefaultView() {
            return getLayoutInflater().inflate(R.layout.card, null, false);
        }

        @Override
        public void bindDefaultView(View view, Integer picture) {
            ((ImageView) view.findViewById(R.id.img)).setImageResource(picture);
        }

        @Override
        public int getItemCount() {
            return pictures.size();
        }

        @Override
        public void remove() {
            pictures.add(pictures.remove(0));
        }
    }
}

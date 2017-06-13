package net.brach.android.stackview.example;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import net.brach.android.stackview.StackView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

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

    private class StackAdapter extends StackView.Adapter {
        private final ArrayList<Integer> pictures;
        private final Random random;

        StackAdapter(Integer... pictures) {
            this.pictures = new ArrayList<>(pictures.length);
            this.pictures.addAll(Arrays.asList(pictures));

            this.random = new Random();
        }

        @Override
        public View createAndBindEmptyView(ViewGroup parent) {
            View view = getLayoutInflater().inflate(R.layout.empty, parent, false);
            view.findViewById(R.id.retry).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pictures.add(R.drawable.cat_1);
                    pictures.add(R.drawable.cat_2);
                    pictures.add(R.drawable.cat_3);
                    pictures.add(R.drawable.cat_4);

                    notifyDataSetChangedOnMainThread();
                }
            });
            return view;
        }

        @Override
        public View onCreateView(ViewGroup parent, Position position) {
            return getLayoutInflater().inflate(R.layout.card, parent, false);
        }

        @Override
        public void onBindView(View view, Position position) {
            ((ImageView) view.findViewById(R.id.img)).setImageResource(pictures.get(position.value));

            view.findViewById(R.id.next).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    remove(random.nextBoolean() ?
                            StackView.Direction.LEFT
                            : StackView.Direction.RIGHT);
                }
            });
        }

        @Override
        public int getItemCount() {
            return pictures.size();
        }

        @Override
        public void remove() {
            pictures.remove(0);
        }
    }
}

using UnityEngine;
using System.Collections;

public class ImagePickerPlugin : MonoBehaviour
{
    public delegate void Callback(string acition, bool success);
    private Callback callback;
#if UNITY_ANDROID
    private AndroidJavaObject androidPlugin;
#endif

    public void SetCallback(Callback cb)
    {
        callback = cb;
    }

    public void Init()
    {
        Debug.Log("ImagePickerPlugin Init called ");
#if UNITY_ANDROID
        androidPlugin = new AndroidJavaObject("com.redoceanred.unity.android.PickerImagePlugin");
        androidPlugin.Call("initPlugin", gameObject.name);
#endif
    }

    public string GetImagePath()
    {
        string result = "";
#if UNITY_ANDROID
        result = androidPlugin.Call<string>("getImagePath");
#endif
        return result;
    }

    public bool PickImage()
    {
        bool result = false;
#if UNITY_ANDROID
        result = androidPlugin.Call<bool>("pickImage");
#endif
        return result;
    }

    public bool CaptureImage()
    {
        bool result = false;
#if UNITY_ANDROID
        result = androidPlugin.Call<bool>("captureImage");
#endif
        return result;
    }

    public void NativeMessage(string message)
    {
        if (callback != null)
        {
            string[] delimiter = { "," };
            string[] splitMessage = message.Split(delimiter, System.StringSplitOptions.None);
            callback(splitMessage[0], bool.Parse(splitMessage[1]));
        }
    }
}

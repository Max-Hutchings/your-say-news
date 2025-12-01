import {Button, View} from "react-native";
import {useAuth} from "@/components/old_auth/AuthContext";
import {useAuthStore} from "@/components/auth/authContext";


export default function SignInScreen(){

    const {login} = useAuthStore();
    console.log("Sign in screen")
    return(
        <View>
            <Button title={"Sign In"} onPress={login} />
        </View>

    )
}
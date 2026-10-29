// vendor: react-native-reusables
import { cn } from '@/lib/utils';
import { View } from 'react-native';

function Skeleton({
  className,
  ...props
}: React.ComponentProps<typeof View> & React.RefAttributes<View>) {
  return <View className={cn('bg-muted animate-pulse motion-reduce:animate-none rounded-md', className)} {...props} />;
}

export { Skeleton };
